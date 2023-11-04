
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;


public class GPSTrimCheat {
    class Entry { 
        Entry(double la, double lo, double hd, double t) {
            lat = la; lon = lo; hdg = hd; trim = t;
        }
        Entry() {}
        double lat, lon, hdg, trim;
        double distance(Entry a) { 
            return Math.abs(Math.sqrt((lat - a.lat) * (lat - a.lat) + (lon - a.lon) * (lon - a.lon) * 47/69))
                * 6068 * 60;
        }
        double latDiff(Entry e) { // difference in feet of the two latitudes
            return Math.abs(lat - e.lat) * 6068 * 60;
        }
        double hdgDiff(Entry a) { // difference in feet of the two points
            double d = Math.abs(hdg - a.hdg);
            if (d > 180) d = 360 - d;
            return d;
        }
    }
    TreeSet<Entry> list = new TreeSet<Entry>(new EntryComparator());

    class EntryComparator implements Comparator<Entry>{
        @Override
        public int compare(Entry a1, Entry a2) {
            if (a1.lat == a2.lat) return 0;
            if (a1.lat > a2.lat) return 1;
            return -1;
        }
    }
    double rad = 0;
    GPSTrimCheat(double radius) { 
        rad = radius;
    }
    void addFile(String fn) {
        BufferedReader fin = null; 
        try {
            fin = new BufferedReader(new FileReader(fn));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        double lastLat = 0, lastLon =0;
        while(fin != null) { 
            try {
                String s = fin.readLine();
                if (s == null) break;
                String[] words = s.split("\\s+");
                Entry e = new Entry();
                e.trim = Double.parseDouble(words[2]); 
                e.lat = Double.parseDouble(words[6]);
                e.lon = Double.parseDouble(words[7]);
                e.hdg = Double.parseDouble(words[8]);
                if (e.lat != lastLat && e.lon != lastLon) {
                    list.add(e);
                }
                lastLat = e.lat;
                lastLon = e.lon;                
            } catch (Exception e) {
                e.printStackTrace();
            }	
        }  
    }
    double get(double lat, double lon, double hdg) {
        Entry cl = new Entry(lat, lon, hdg, 0);
        Average avg = new Average();
        //Entry f = null;
        for (Entry f : list) { 
            if (f.hdgDiff(cl) < 20 && f.distance(cl) < rad) {
                avg.add(f.trim); 
            }
        } 
        trim = avg.calculate();
        count = avg.count;

        trim = Math.min(maxTrim, Math.max(-maxTrim, trim));
        return trim;
    }
    double trim = 0, count = 0;
    double maxTrim = .15;
}

