package com.softknife.data.dataprovider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;

/**
 * @author Sasha Matsaylo on 6/11/21
 * @project qreasp
 */

public class DataProviderHelper {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private List<Object> dataSet;

    private DataProviderHelper() {
    }

    /** Lazily created, thread-safe without locking: the JVM initialises the holder class once. */
    private static final class Holder {
        private static final DataProviderHelper INSTANCE = new DataProviderHelper();
    }

    public static DataProviderHelper getInstance() {
        return Holder.INSTANCE;
    }

    public void initializeSetOfData(List<Object> dataSet){
        if(dataSet == null){
            throw new NullPointerException("Parameter Type cannot be null");
        }
        this.dataSet = dataSet;
    }

    public List<Object> getDataSet(){
        if(this.dataSet == null){
            throw new NullPointerException("Data set has not been set, please use initializeSetOfData() method");
        }
        return this.dataSet;
    }

    public boolean isDataSet(){
        if(this.dataSet == null){
            return false;
        }
        return true;
    }


}



