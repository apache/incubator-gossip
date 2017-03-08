package org.apache.gossip.crdt;


import java.lang.annotation.ElementType;
import java.util.HashMap;
import java.util.Map;

public class GrowOnlyCounter implements CrdtCounter<Long, GrowOnlyCounter> {


    private final Map<String, Long> counters = new HashMap<>();
    private final String myID;

    public GrowOnlyCounter(String myID) {
        this.myID = myID;
        counters.putIfAbsent(myID, 0L);
    }

    private GrowOnlyCounter(String myID, Long count) {
        this.myID = myID;
        counters.putIfAbsent(myID, count);
    }

    private GrowOnlyCounter(String myID, Map<String, Long> counters) {
        this.myID = myID;
        this.counters.putAll(counters);
    }


    @Override
    public GrowOnlyCounter merge(GrowOnlyCounter other) {
        //System.out.println(other);
        this.counters.putIfAbsent(other.myID, other.counters.get(other.myID));
        Map<String , Long> updatedCounter = new HashMap<>();
        for (Map.Entry<String, Long> entry : this.counters.entrySet()) {
            String key = entry.getKey();
            Long value = entry.getValue();

            if(other.counters.containsKey(key)){
                Long newValue = Math.max(value,other.counters.get(key));
                updatedCounter.put(key,newValue);
            }else {
                updatedCounter.put(key,value);
            }

        }

        return new GrowOnlyCounter(myID,updatedCounter);
    }

    @Override
    public Long value() {
        Long globalCount = 0L;
        for (Long increment : counters.values()) {
            globalCount += increment;
        }
        return globalCount;
    }

    @Override
    public GrowOnlyCounter optimize() {
        return new GrowOnlyCounter(myID, counters.get(myID));
    }

    @Override
    public boolean equals(Object obj) {

        GrowOnlyCounter other = (GrowOnlyCounter) obj;

        return value().longValue() == other.value().longValue();
    }

    public void increase() {
        counters.replace(myID, counters.get(myID) + 1);
    }

    public void increaseBy(int count) {
        counters.replace(myID, counters.get(myID) + count);
    }


    @Override
    public String toString() {
        return "GrowOnlyCounter [counters= " + counters + ", Value=" + value() + "]";
    }

    String getMyID() {
        return myID;
    }

    Map<String, Long> getCounters() {
        return counters;
    }
}
