
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
            lat = la; lon = lo; hdg = hd; trim = 0; buttons = 0; curve = 0; speed = 0; time = 0;
        }
        Entry() {}
        double time, lat, lon, hdg, trim, curve, speed;
        int buttons;
        double distance(Entry a) { 
            return Math.abs(Math.sqrt((lat - a.lat) * (lat - a.lat) + (lon - a.lon) * (lon - a.lon) * 47/69))
                * 6068 * 60;
        }
        double latDiff(Entry e) { // difference in feet of the two latitudes
            return Math.abs(lat - e.lat) * 6068 * 60;
        }
        double hdgDiff(Entry a) { // difference in feet of the two points
            double d = hdg - a.hdg;
            if (d > 180) d = 360 - d;
            if (d < -180) d = d + 360;
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
    double degreeDiff(double a, double b) { 
        return a-b; 
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
        Entry last = null;
        int line = 0;
        String s = new String("");
        while(fin != null) { 
            try {
                s = fin.readLine();
                if (s == null) break;
                Entry e = new Entry();
                e.trim = reDouble(".*st\\s+([-+]?[0-9.]+)", s);
                e.time = reDouble("t\\s+([-+]?[0-9.]+)", s);
                double corr =  reDouble(".*corr\\s+([-+]?[0-9.]+)", s);
                double gpstrim =  reDouble(".*\\sgpstrim\\s+([-+]?[0-9.]+)", s);
                double strim =  reDouble(".*\\sstrim\\s+([-+]?[0-9.]+)", s);
                e.lat = reDouble(".*lat\\s+([-+]?[0-9.]+)", s);
                e.lon = reDouble(".*lon\\s+([-+]?[0-9.]+)", s);
                e.hdg = reDouble(".*hdg\\s+([-+]?[0-9.]+)", s);
                e.speed = reDouble(".*speed\\s+([-+]?[0-9.]+)", s);
                e.buttons = (int)reDouble(".*but\\s+([-+]?[0-9.]+)", s);
                if (Math.abs(corr + gpstrim + strim) > Math.abs(e.trim))
                    e.trim = corr + gpstrim + strim;
                
                if (last != null) {
                    double timeD = e.time - last.time;
                    double hdgD = last.hdgDiff(e);
                    double curve = hdgD * 100000 / (timeD * e.speed);
                    curve = Math.max(-15, Math.min(15, curve));
                    e.curve = curve;
                }
                if (last == null || (e.lat != last.lat && e.lon != last.lon)) {
                    list.add(e);
                }
                last = e;
                line++;
            } catch (Exception e) {
                //System.out.printf("file %s, line %d: %s\n", fn, line, s);
                //e.printStackTrace();
            }	
        }  
    }
    double get(double lat, double lon, double hdg) {
        Entry cl = new Entry(lat, lon, hdg);
        Average steerAvg = new Average();
        Average curveAvg = new Average();
        //Entry f = null;
        buttons = 0;
        for (Entry f : list) { 
            if (Math.abs(f.hdgDiff(cl)) < 30 && f.distance(cl) < rad) {
                steerAvg.add(f.trim); 
                curveAvg.add(f.curve); 
                buttons = buttons | f.buttons;
            }
        } 
        trim = steerAvg.calculate() * 0.4;
        curve = curveAvg.calculate() * -0.012;
        count = steerAvg.count;


        trim = Math.min(maxTrim, Math.max(-maxTrim, trim));
        curve = Math.min(maxCurve, Math.max(-maxCurve, curve));

        return trim + curve;
    }
    double trim = 0, count = 0, curve = 0;
    double maxTrim = .15;
    double maxCurve = .20;
    double trimThresh = 0.10;
    int buttons = 0;
}

