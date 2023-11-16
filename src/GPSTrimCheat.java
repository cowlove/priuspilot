
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;


import java.util.*;
import java.util.regex.*;

public class GPSTrimCheat {
    class Entry { 
        Entry(double la, double lo, double hd) {
            lat = la; lon = lo; hdg = hd; trim = 0; buttons = 0;
        }
        Entry() {}
        double lat, lon, hdg, trim;
        int buttons;
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
    String re(String pat, String s) { 
        try { 
            Pattern p = Pattern.compile(pat);
            Matcher m = p.matcher(s);
            m.find();
            return m.group(1);
        } catch(Exception e) { 
            return "";
        }
    }
    double reDouble(String p, String s) { 
        return Double.parseDouble(re(p, s));
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
                e.trim = reDouble(".*st\\s+([-+]?[0-9.]+)", s);
                double corr =  reDouble(".*corr\\s+([-+]?[0-9.]+)", s);
                double gpstrim =  reDouble(".*\\sgpstrim\\s+([-+]?[0-9.]+)", s);
                double strim =  reDouble(".*\\sstrim\\s+([-+]?[0-9.]+)", s);
                e.lat = reDouble(".*lat\\s+([-+]?[0-9.]+)", s);
                e.lon = reDouble(".*lon\\s+([-+]?[0-9.]+)", s);
                e.hdg = reDouble(".*hdg\\s+([-+]?[0-9.]+)", s);
                e.buttons = (int)reDouble(".*but\\s+([-+]?[0-9.]+)", s);
                if (Math.abs(corr + gpstrim + strim) > Math.abs(e.trim))
                    e.trim = corr + gpstrim + strim;
                if (Math.abs(e.trim) < trimThresh)
                    e.trim = 0;
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
        Entry cl = new Entry(lat, lon, hdg);
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
    double maxTrim = .20;
    double trimThresh = 0.10;
}

