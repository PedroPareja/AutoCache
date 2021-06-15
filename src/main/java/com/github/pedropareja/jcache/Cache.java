package com.github.pedropareja.jcache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * <b>Generic Cache</b>
 *
 * <p>Generic self managed cache
 *
 * @param <K> Key type
 * @param <V> Value type
 * @param <E> Exception type
 */


public interface Cache <K extends Comparable<K>,V,E extends Exception>
{
    /**
     * Gets an element. If the element is not cached it is automatically loaded through the {@link DataLoader} and
     * included in the cache.
     *
     * @param key Key
     * @return Data value
     * @throws E if data loader fails
     */

    V get(K key) throws E;


    /**
     * Gets an element as a {@link Future} using the {@link ExecutorService} provided.
     * If the element is not cached it is automatically loaded through the {@link DataLoader} and
     * included in the cache.
     *
     * @param key Key
     * @param executorService {@link ExecutorService}
     * @return {@link Future} containing data value
     * @throws E if data loader fails
     */

    Future<V> getFuture(K key, ExecutorService executorService) throws E;


    /**
     * Sets manually an element into the cache.
     *
     * <p>To be used only in specific cases as the {@link #get(Comparable) get} method should include any data in the cache
     * when required
     *
     * @param key Key
     * @param value Value
     */

    void set(K key, V value);


    /**
     * Sets the minutes to expire setting.
     * This defines how many minutes any entry lasts
     *
     * @param minutesToExpire Minutes to expire
     */

    void setMinutesToExpire(int minutesToExpire);


    /**
     * Sets the minutes to purge setting.
     * This defines how many minutes passes until a cache purge is triggered in the next request
     *
     * @param minutesToPurge Minutes to purge
     */

    void setMinutesToPurge(int minutesToPurge);


    /**
     * Sets max element count setting.
     * This defines how many elements can be cached in memory until a purge is triggered
     *
     * @param maxElementCount Max element count
     */

    void setMaxElementCount(int maxElementCount);


    /**
     * Sets safety clear setting.
     * This defines if a emergency cache clear is triggered if after a purge the element count surpass the max
     *
     * @param safetyClear Safety clear setting
     */

    void setSafetyClear(boolean safetyClear);


    /**
     * Sets time purge option.
     * Sets which action triggered to purge the cache when purge time is reached
     *
     * @param timePurgeOption {@link PurgeOption}
     */

    void setTimePurgeOption(PurgeOption timePurgeOption);


    /**
     * Sets the element count purge option
     * Sets which action triggered to purge the cache when element max count is reached
     *
     * @param elementCountPurgeOption {@link PurgeOption}
     */

    void setElementCountPurgeOption(PurgeOption elementCountPurgeOption);


    /**
     * Gets the cache size
     *
     * @return number of elements in cache
     */

    int size();


    /**
     * Clears the cache. Deletes all elements.
     */

    void clear();


    /**
     * Sets an element as expired
     *
     * @param key Key
     */

    void setDirty(K key);


    /**
     * Sets the check function to decide if an element is going to be stored in the cache
     *
     * @param storeChecker Store checker
     */

    void setStoreChecker(StoreChecker<V> storeChecker);


    /**
     * Sets the event callback to be triggered in the case of a cache miss
     *
     * @param cacheMissEvent
     */

    void setCacheMissEvent(CacheMissEvent<K> cacheMissEvent);


    /**
     * Sets the event callback to be triggered in the case of a cache hit
     *
     * @param cacheHitEvent
     */

    void setCacheHitEvent(ElementEvent<K,V> cacheHitEvent);


    /**
     * Sets the event callback to be triggered in the case of an element load from the external source
     *
     * @param elementLoadEvent
     */

    void setElementLoadEvent(ElementEvent<K,V> elementLoadEvent);


    /**
     * Sets the event callback to be triggered in the case of an element save in the cache
     *
     * @param elementSaveEvent
     */

    void setElementSaveEvent(ElementEvent<K,V> elementSaveEvent);


    /**
     * Sets the event callback to be triggered in the case of the removal of an element in the cache
     *
     * @param elementRemoveEvent
     */

    void setElementRemoveEvent(ElementEvent<K,V> elementRemoveEvent);


    /**
     * Sets if the expiration time is automatically extended when an element is requested
     *
     * @param expirationExtensionOnRetrieval
     */

    void setExpirationExtensionOnRetrieval(boolean expirationExtensionOnRetrieval);


    /**
     * <b>Data Loader</b>
     *
     * <p>Defines how data is loaded when needed
     *
     * @param <K> Key type
     * @param <V> Value type
     * @param <E> Exception type
     */

    @FunctionalInterface
    public interface DataLoader<K,V,E extends Exception>
    {
        V loadData(K key) throws E;
    }


    /**
     * <b>Purge Option</b>
     */

    public enum PurgeOption
    {
        /**
         * Option to delete all the elements
         */

        CLEAR,

        /**
         * Option to selectively delete only the expired elements. This operation could be time consuming: proceed with caution
         */

        PURGE
    }

    /**
     * <b>Store Checker</b>
     *
     * <p>Defines a function to decide if an element is going to be stored or not</p>
     *
     * @param <V>
     */

    @FunctionalInterface
    public interface StoreChecker<V>
    {
        boolean hasToBeStored(V value);
    }

    /**
     * <b>Cache Miss Event</b>
     *
     * <p>Defines a callback to be triggered in the case of a cache miss</p>
     *
     * @param <K>
     */

    public interface CacheMissEvent<K extends Comparable<K>>
    {
        void onEvent(Cache<K,?,?> cache, K key);
    }

    /**
     * <b>Cache Element Event</b>
     *
     * <p>Defines a callback to be triggered in the case of a element specific operation</p>
     *
     * @param <K>
     * @param <V>
     */

    public interface ElementEvent <K extends Comparable<K>,V>
    {
        void onEvent(Cache<K,V,?> cache, K key, V value);
    }
}
