package dev.jasper.editor.textview;

import java.text.AttributedCharacterIterator;
import java.util.*;

/** Immutable indexed style runs; constructing many syntax runs never repeatedly scans preceding runs. */
final class AttributedRuns {
    record Run(int start,int end,Map<AttributedCharacterIterator.Attribute,Object> attributes) {
        Run {attributes=Map.copyOf(attributes);}
    }
    private final String text;
    private final List<Run> runs;
    private final int[] starts;
    private final Set<AttributedCharacterIterator.Attribute> keys;
    private final Map<AttributedCharacterIterator.Attribute,int[]> groupStarts=new HashMap<>(),groupEnds=new HashMap<>();
    AttributedRuns(String text,List<Run> runs) {
        this.text=text;this.runs=List.copyOf(runs);starts=runs.stream().mapToInt(Run::start).toArray();
        var all=new HashSet<AttributedCharacterIterator.Attribute>();runs.forEach(run -> all.addAll(run.attributes.keySet()));keys=Set.copyOf(all);
        for(var key:keys) {
            var beginnings=new int[runs.size()];var ends=new int[runs.size()];
            for(int first=0;first<runs.size();) {
                int last=first+1;Object value=runs.get(first).attributes.get(key);
                while(last<runs.size() && Objects.equals(value,runs.get(last).attributes.get(key)))last++;
                Arrays.fill(beginnings,first,last,runs.get(first).start);Arrays.fill(ends,first,last,runs.get(last-1).end);first=last;
            }
            groupStarts.put(key,beginnings);groupEnds.put(key,ends);
        }
    }
    AttributedCharacterIterator iterator(int begin,int end) {return new Iterator(begin,end);}
    private final class Iterator implements AttributedCharacterIterator {
        private final int begin,end;
        private int index,run;
        Iterator(int begin,int end){this.begin=begin;this.end=end;setIndex(begin);}
        @Override public char setIndex(int position) {
            if(position<begin || position>end)throw new IllegalArgumentException("Invalid index");index=position;
            if(index<end && (run>=runs.size() || index<runs.get(run).start || index>=runs.get(run).end)) {
                int found=Arrays.binarySearch(starts,index);run=found>=0?found:-found-2;
            }
            return current();
        }
        @Override public char current(){return index==end?DONE:text.charAt(index);}
        @Override public char first(){return setIndex(begin);}
        @Override public char last(){return setIndex(end==begin?end:end-1);}
        @Override public char next(){return index==end?DONE:setIndex(index+1);}
        @Override public char previous(){return index==begin?DONE:setIndex(index-1);}
        @Override public int getBeginIndex(){return begin;}
        @Override public int getEndIndex(){return end;}
        @Override public int getIndex(){return index;}
        @Override public Set<Attribute> getAllAttributeKeys(){return keys;}
        @Override public Map<Attribute,Object> getAttributes(){return index==end?Map.of():runs.get(run).attributes;}
        @Override public Object getAttribute(Attribute key){return getAttributes().get(key);}
        @Override public int getRunStart(){return index==end?end:Math.max(begin,runs.get(run).start);}
        @Override public int getRunLimit(){return index==end?end:Math.min(end,runs.get(run).end);}
        @Override public int getRunStart(Attribute key){var bounds=groupStarts.get(key);return index==end?end:bounds==null?begin:Math.max(begin,bounds[run]);}
        @Override public int getRunLimit(Attribute key){var bounds=groupEnds.get(key);return index==end?end:bounds==null?end:Math.min(end,bounds[run]);}
        @Override public int getRunStart(Set<? extends Attribute> attributes){int value=begin;for(var key:attributes)value=Math.max(value,getRunStart(key));return value;}
        @Override public int getRunLimit(Set<? extends Attribute> attributes){int value=end;for(var key:attributes)value=Math.min(value,getRunLimit(key));return value;}
        @Override public Object clone(){try{return super.clone();}catch(CloneNotSupportedException impossible){throw new AssertionError(impossible);}}
    }
}
