package com.softknife.util.common;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RBFileUtils {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private RBFileUtils() {
    }

    /** Lazily created, thread-safe without locking: the JVM initialises the holder class once. */
    private static final class Holder {
        private static final RBFileUtils INSTANCE = new RBFileUtils();
    }

    public static RBFileUtils getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * A classpath resource as a {@link File}.
     *
     * @throws IllegalArgumentException when the resource does not exist, or lives inside a jar and so
     *                                  has no file of its own — use {@link #getFileOnClassPathAsString} then
     */
    public static File getFileOnClassPath(String filePath) {
        return getInstance().getFileAsFileFromClassPath(filePath);
    }

    public static byte[] getFileOnClassPathByteArray(String filePath) {
        return getInstance().getFileAsByteArrayFromClassPath(filePath);
    }

    public static String getFileOnClassPathAsString(String filePath) {
        return getInstance().getFileAsStringFromClassPath(filePath);
    }

    private String getFileAsStringFromClassPath(String relativePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(relativePath)) {
            if (inputStream == null) {
                logger.warn("Resource not found on classpath: {}", relativePath);
                return null;
            }
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Error reading file '{}': {}", relativePath, e.getLocalizedMessage());
            return null;
        }
    }


    private byte[] getFileAsByteArrayFromClassPath(String relativePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(relativePath)) {
            if (inputStream == null) {
                logger.warn("Resource not found on classpath: {}", relativePath);
                return new byte[0];
            }
            return IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            logger.error("Error reading file '{}': {}", relativePath, e.getLocalizedMessage());
            return new byte[0];
        }
    }

    private File getFileAsFileFromClassPath(String relativePath) {
        URL resource = getClass().getClassLoader().getResource(relativePath);
        if (resource == null) {
            throw new IllegalArgumentException("Resource not found on classpath: " + relativePath);
        }
        if (!"file".equals(resource.getProtocol())) {
            throw new IllegalArgumentException("Resource '" + relativePath + "' is inside a " + resource.getProtocol()
                    + " archive and has no file of its own; read it with getFileOnClassPathAsString or getFileOnClassPathByteArray");
        }
        try {
            // toURI, not getPath: getPath keeps %20 for a space and names a file that does not exist.
            return Paths.get(resource.toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Resource '" + relativePath + "' has an invalid location: " + resource, e);
        }
    }

    public Map<String, String> readFilesAsStringIntoMap(String directory, String fileType) throws IOException {
        Map<String, String> templateMap = new HashMap<>();
        List<File> files;
        try (Stream<Path> paths = Files.list(Paths.get(directory))) {
            files = paths
                    .filter(path -> path.toString().endsWith(fileType))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
        for (File file : files) {
            try {
                templateMap.put(file.getName().split("\\.")[0], FileUtils.readFileToString(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }
        return templateMap;
    }

    public List<File> readAllFiles(String startDir, String[] extensions, boolean recursive) {
        if (startDir == null) {
            throw new IllegalArgumentException("startDir must not be null");
        }
        Collection<File> files = FileUtils.listFiles(new File(startDir), extensions, recursive);
        return new ArrayList<>(files);
    }
}
