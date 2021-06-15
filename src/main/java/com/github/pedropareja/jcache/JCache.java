package com.github.pedropareja.jcache;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * <b>JCache</b>
 *
 * <p>Generic self managed cache
 *
 * @param <K> Key type
 * @param <V> Value type
 * @param <E> Exception type
 */

public class JCache <K extends Comparable<K>,V,E extends Exception> implements Cache<K,V,E>
{
    private static final int DEFAULT_MINUTES_TO_EXPIRE = 30;
    private static final int DEFAULT_MINUTES_TO_PURGE = 120;
    private static final int DEFAULT_MAX_ELEMENT_COUNT = 20000;
    private static final boolean DEFAULT_SAFETY_CLEAR = true;
    private static final PurgeOption DEFAULT_TIME_PURGE_OPTION = PurgeOption.PURGE;
    private static final PurgeOption DEFAULT_ELEMENT_COUNT_PURGE_OPTION = PurgeOption.CLEAR;
    private static final boolean DEFAULT_EXPIRATION_EXTENSION_ON_RETRIEVAL = false;

    private final Map<K,Entry<V>> map;
    private final DataLoader<K,V,E> dataLoader;
    private long nextPurgeTime;

    private int minutesToExpire = DEFAULT_MINUTES_TO_EXPIRE;
    private int minutesToPurge = DEFAULT_MINUTES_TO_PURGE;
    private int maxElementCount = DEFAULT_MAX_ELEMENT_COUNT;
    private boolean safetyClear = DEFAULT_SAFETY_CLEAR;

    private PurgeOption timePurgeOption = DEFAULT_TIME_PURGE_OPTION;
    private PurgeOption elementCountPurgeOption = DEFAULT_ELEMENT_COUNT_PURGE_OPTION;

    private StoreChecker<V> storeChecker = null;
    private CacheMissEvent<K> cacheMissEvent = null;
    private ElementEvent<K,V> cacheHitEvent = null;
    private ElementEvent<K,V> elementLoadEvent = null;
    private ElementEvent<K,V> elementSaveEvent = null;
    private ElementEvent<K,V> elementRemoveEvent = null;

    private boolean expirationExtensionOnRetrieval = DEFAULT_EXPIRATION_EXTENSION_ON_RETRIEVAL;


    /**
     * Cache constructor
     *
     * @param dataLoader {@link DataLoader}: defines how data is loaded
     */

    public JCache(DataLoader<K,V,E> dataLoader)
    {
        this(dataLoader, () -> new ConcurrentHashMap<>());
    }

    /**
     * Cache constructor
     *
     * @param dataLoader {@link DataLoader}: defines how data is loaded
     * @param mapFactory {@link MapFactory}: defines the specific {@link Map} implementation which will be used
     * @param <T> MapFactory Exception
     * @throws T if Map instantiation fails
     */

    @SuppressWarnings("unchecked")
    public <T extends Exception> JCache(DataLoader<K,V,E> dataLoader, MapFactory<K,T> mapFactory) throws T
    {
        this.map = (Map<K,Entry<V>>) mapFactory.newInstance();
        this.dataLoader = dataLoader;
        setNextPurge(System.currentTimeMillis());
    }

    private void setNextPurge(long nowTime)
    {
        nextPurgeTime = nowTime + minutesToPurge * 60 * 1000;
    }

    private void checkPurge(long nowTime)
    {
        if(nowTime > nextPurgeTime)
            autoPurge(timePurgeOption, nowTime);
        else if(map.size() >= maxElementCount)
            autoPurge(elementCountPurgeOption, nowTime);
    }

    private void autoPurge(PurgeOption purgeOption, long nowTime)
    {
        setNextPurge(nowTime);

        switch (purgeOption)
        {
            case CLEAR:
                clear();
                break;

            case PURGE:
                purge(nowTime);
                break;
        }

        if(safetyClear && map.size() > maxElementCount)
            clear();
    }

    private static long getExpireTime(long currentTime, int minutesToExpire)
    {
        return currentTime + minutesToExpire * 60000;
    }

    /**
     * Gets an element. If the element is not cached it is automatically loaded through the {@link DataLoader} and
     * included in the cache.
     *
     * @param key Key
     * @return Data value
     * @throws E if data loader fails
     */

    @Override
    public V get(K key) throws E
    {
        long time = System.currentTimeMillis();
        return get(key, time);
    }

    /**
     * Gets an element. If the element is not cached it is automatically loaded through the {@link DataLoader} and
     * included in the cache.
     *
     * @param key Key
     * @param time Current timestamp
     * @return Data value
     * @throws E if data loader fails
     */

    public V get(K key, long time) throws E
    {
        checkPurge(time);

        Entry<V> resultEntry = map.get(key);

        if (resultEntry == null || (resultEntry != null && resultEntry.expireTime < time))
        {
            if(cacheMissEvent != null)
                cacheMissEvent.onEvent(this, key);

            V result = dataLoader.loadData(key);

            if(elementLoadEvent != null)
                elementLoadEvent.onEvent(this, key, result);

            if(storeChecker == null || storeChecker.hasToBeStored(result))
            {
                resultEntry = new Entry<>(result, getExpireTime(time, minutesToExpire));
                put(key, resultEntry);
            }

            return result;
        }
        else
        {
            if(expirationExtensionOnRetrieval)
                resultEntry.expireTime = getExpireTime(time, minutesToExpire);

            if (cacheHitEvent != null)
                cacheHitEvent.onEvent(this, key, resultEntry.value);
        }

        return resultEntry.value;
    }

    /**
     * Gets an element as a {@link Future} using the {@link ExecutorService} provided.
     * If the element is not cached it is automatically loaded through the {@link DataLoader} and
     * included in the cache.
     *
     * @param key Key
     * @param time Current timestamp
     * @param executorService {@link ExecutorService}
     * @return {@link Future} containing data value
     * @throws E if data loader fails
     */

    public Future<V> getFuture(K key, long time, ExecutorService executorService) throws E
    {
        return executorService.submit(()->get(key, time));
    }

    @Override
    public Future<V> getFuture(K key, ExecutorService executorService) throws E
    {
        long time = System.currentTimeMillis();
        return getFuture(key, time, executorService);
    }

    /**
     * Sets manually an element into the cache.
     *
     * <p>To be used only in specific cases as the {@link #get(Comparable) get} method should include any data in the cache
     * when required
     *
     * @param key Key
     * @param value Value
     */

    @Override
    public void set(K key, V value)
    {
        Entry<V> newEntry = new Entry<>(value, getExpireTime(System.currentTimeMillis(), minutesToExpire));
        put(key, newEntry);
    }

    private void put(K key, Entry<V> entry)
    {
        onRemove(key);

        map.put(key, entry);

        if(elementSaveEvent != null)
            elementSaveEvent.onEvent(this, key, entry.value);
    }

    /**
     * Gets the minutes to expire setting.
     * This defines how many minutes any entry lasts
     *
     * @return Minutes to expire
     */

    public int getMinutesToExpire()
    {
        return minutesToExpire;
    }

    /**
     * Sets the minutes to expire setting.
     * This defines how many minutes any entry lasts
     *
     * @param minutesToExpire Minutes to expire
     */

    @Override
    public void setMinutesToExpire(int minutesToExpire)
    {
        this.minutesToExpire = minutesToExpire;
    }

    /**
     * Gets the minutes to purge setting.
     * This defines how many minutes passes until a cache purge is triggered in the next request
     *
     * @return Minutes to purge
     */

    public int getMinutesToPurge()
    {
        return minutesToPurge;
    }

    /**
     * Sets the minutes to purge setting.
     * This defines how many minutes passes until a cache purge is triggered in the next request
     *
     * @param minutesToPurge Minutes to purge
     */

    @Override
    public void setMinutesToPurge(int minutesToPurge)
    {
        this.minutesToPurge = minutesToPurge;
        setNextPurge(System.currentTimeMillis());
    }

    /**
     * Gets the max element count setting.
     * This defines how many elements can be cached in memory until a purge is triggered
     *
     * @return Max element count
     */

    public int getMaxElementCount()
    {
        return maxElementCount;
    }

    /**
     * Sets max element count setting.
     * This defines how many elements can be cached in memory until a purge is triggered
     *
     * @param maxElementCount Max element count
     */

    @Override
    public void setMaxElementCount(int maxElementCount)
    {
        this.maxElementCount = maxElementCount;
    }

    /**
     * Gets safety clear setting.
     * This defines if a emergency cache clear is triggered if after a purge the element count surpass the max
     *
     * @return Safety clear setting
     */

    public boolean isSafetyClear()
    {
        return safetyClear;
    }

    /**
     * Sets safety clear setting.
     * This defines if a emergency cache clear is triggered if after a purge the element count surpass the max
     *
     * @param safetyClear Safety clear setting
     */

    @Override
    public void setSafetyClear(boolean safetyClear)
    {
        this.safetyClear = safetyClear;
    }

    /**
     * Gets time purge option.
     * Sets which action triggered to purge the cache when purge time is reached
     *
     * @return {@link PurgeOption}
     */

    public PurgeOption getTimePurgeOption()
    {
        return timePurgeOption;
    }

    /**
     * Sets time purge option.
     * Sets which action triggered to purge the cache when purge time is reached
     *
     * @param timePurgeOption {@link PurgeOption}
     */

    @Override
    public void setTimePurgeOption(PurgeOption timePurgeOption)
    {
        this.timePurgeOption = timePurgeOption;
    }

    /**
     * Gets element count purge option
     * Sets which action triggered to purge the cache when element max count is reached
     *
     * @return {@link PurgeOption}
     */

    public PurgeOption getElementCountPurgeOption()
    {
        return elementCountPurgeOption;
    }

    /**
     * Sets the element count purge option
     * Sets which action triggered to purge the cache when element max count is reached
     *
     * @param elementCountPurgeOption {@link PurgeOption}
     */

    @Override
    public void setElementCountPurgeOption(PurgeOption elementCountPurgeOption)
    {
        this.elementCountPurgeOption = elementCountPurgeOption;
    }

    /**
     * Gets the cache size
     *
     * @return number of elements in cache
     */

    @Override
    public int size()
    {
        return map.size();
    }

    /**
     * Clears the cache. Deletes all elements.
     */

    @Override
    public void clear()
    {
        if(elementRemoveEvent != null)
            for(Map.Entry<K, Entry<V>> mapEntry: map.entrySet())
                elementRemoveEvent.onEvent(this, mapEntry.getKey(), mapEntry.getValue().value);

        map.clear();
    }

    public void purge(long nowTime)
    {
        Set<K> deleteKeySet = new TreeSet<>();
        for(Map.Entry<K, Entry<V>> mapEntry: map.entrySet())
            if(mapEntry.getValue().expireTime < nowTime)
            {
                deleteKeySet.add(mapEntry.getKey());
                if(elementRemoveEvent != null)
                    elementRemoveEvent.onEvent(this, mapEntry.getKey(), mapEntry.getValue().value);
            }

        for(K key: deleteKeySet)
            map.remove(key);
    }

    /**
     * Sets an element as expired
     *
     * @param key Key
     */

    @Override
    public void setDirty(K key)
    {
        remove(key);
    }


    private void remove(K key)
    {
        onRemove(key);
        map.remove(key);
    }

    private void onRemove(K key)
    {
        if(elementRemoveEvent != null)
        {
            Entry<V> entry = map.get(key);

            if(entry != null)
                elementRemoveEvent.onEvent(this, key, entry.value);
        }
    }

    /**
     * Sets the check function to decide if an element is going to be stored in the cache
     *
     * @param storeChecker Store checker
     */

    @Override
    public void setStoreChecker(StoreChecker<V> storeChecker)
    {
        this.storeChecker = storeChecker;
    }

    /**
     * Sets the event callback to be triggered in the case of a cache miss
     *
     * @param cacheMissEvent
     */

    @Override
    public void setCacheMissEvent(CacheMissEvent<K> cacheMissEvent)
    {
        this.cacheMissEvent = cacheMissEvent;
    }

    /**
     * Sets the event callback to be triggered in the case of a cache hit
     *
     * @param cacheHitEvent
     */

    @Override
    public void setCacheHitEvent(ElementEvent<K,V> cacheHitEvent)
    {
        this.cacheHitEvent = cacheHitEvent;
    }

    /**
     * Sets the event callback to be triggered in the case of an element load from the external source
     *
     * @param elementLoadEvent
     */

    @Override
    public void setElementLoadEvent(ElementEvent<K,V> elementLoadEvent)
    {
        this.elementLoadEvent = elementLoadEvent;
    }

    /**
     * Sets the event callback to be triggered in the case of an element save in the cache
     *
     * @param elementSaveEvent
     */

    @Override
    public void setElementSaveEvent(ElementEvent<K,V> elementSaveEvent)
    {
        this.elementSaveEvent = elementSaveEvent;
    }

    /**
     * Sets the event callback to be triggered in the case of the removal of an element in the cache
     *
     * @param elementRemoveEvent
     */

    @Override
    public void setElementRemoveEvent(ElementEvent<K,V> elementRemoveEvent)
    {
        this.elementRemoveEvent = elementRemoveEvent;
    }

    public boolean getExpirationExtensionOnRetrieval()
    {
        return expirationExtensionOnRetrieval;
    }

    /**
     * Sets if the expiration time is automatically extended when an element is requested
     *
     * @param expirationExtensionOnRetrieval
     */

    @Override
    public void setExpirationExtensionOnRetrieval(boolean expirationExtensionOnRetrieval)
    {
        this.expirationExtensionOnRetrieval = expirationExtensionOnRetrieval;
    }

    private static class Entry<V>
    {
        final V value;
        long expireTime;

        Entry(V value, long expireTime)
        {
            this.value = value;
            this.expireTime = expireTime;
        }
    }

    /**
     * <b>Map Factory</b>
     *
     * <p>Defines the cache {@link Map} instantiation
     *
     * @param <K> Key type
     * @param <E> Exception Type
     */

    @FunctionalInterface
    public interface MapFactory<K,E extends Exception>
    {
        Map<K,?> newInstance() throws E;
    }
}
