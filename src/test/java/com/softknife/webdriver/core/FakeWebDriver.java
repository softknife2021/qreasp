package com.softknife.webdriver.core;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Interactive;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A browserless WebDriver for unit tests of the executor.
 *
 * <p>Every interface method answers with a harmless default: another fake for interface return
 * types ({@code navigate()}, {@code manage().window()}, a found element), {@code null}/0/false/""
 * otherwise. {@code findElement} can be told to fail a number of times first, and every call is
 * recorded so a test can assert what the executor actually asked the driver to do.
 */
final class FakeWebDriver {

    final AtomicInteger findElementCalls = new AtomicInteger();
    final List<String> calls = new ArrayList<>();
    private volatile int findElementFailuresRemaining;

    private FakeWebDriver() {
    }

    static FakeWebDriver create() {
        return new FakeWebDriver();
    }

    FakeWebDriver failFindElement(int times) {
        this.findElementFailuresRemaining = times;
        return this;
    }

    WebDriver driver() {
        return (WebDriver) proxy(new Class<?>[]{WebDriver.class, JavascriptExecutor.class, TakesScreenshot.class, Interactive.class});
    }

    private Object proxy(Class<?>[] interfaces) {
        InvocationHandler handler = new Handler();
        return Proxy.newProxyInstance(FakeWebDriver.class.getClassLoader(), interfaces, handler);
    }

    private final class Handler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            synchronized (calls) {
                calls.add(method.getDeclaringClass().getSimpleName() + "." + name);
            }
            if ("toString".equals(name)) {
                return "FakeWebDriver";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("findElement".equals(name)) {
                findElementCalls.incrementAndGet();
                if (findElementFailuresRemaining > 0) {
                    findElementFailuresRemaining--;
                    throw new NoSuchElementException("fake: not yet");
                }
            }
            if ("getScreenshotAs".equals(name)) {
                return new byte[0];
            }
            if ("isDisplayed".equals(name) || "isEnabled".equals(name)) {
                return true;
            }
            return defaultFor(method.getReturnType());
        }
    }

    private Object defaultFor(Class<?> type) {
        if (type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == double.class || type == float.class) {
            return 0.0;
        }
        if (type == String.class) {
            return "";
        }
        if (type == List.class) {
            return new ArrayList<>();
        }
        if (type == java.util.Set.class) {
            return new java.util.HashSet<>();
        }
        if (type == WebElement.class) {
            return proxy(new Class<?>[]{WebElement.class, org.openqa.selenium.interactions.Locatable.class,
                    org.openqa.selenium.WrapsDriver.class});
        }
        if (type.isInterface()) {
            return proxy(new Class<?>[]{type});
        }
        return null;
    }
}
