#!/usr/bin/python 

import re
import sys
from math import sin,cos,atan2,sqrt
import math
import copy

def hdgDiff(h1, h2):
    r = h1 - h2;
    if (r < 180): r = r + 360
    if (r > 180): r = r - 360
    return r

def distance(p1, p2):
    R = 6371e3
    dlat = (p2.lat - p1.lat) * math.pi / 180.0
    dlon = (p2.lon - p1.lon) * math.pi / 180.0 
        
    a = sin(dlat/2) * sin(dlat/2) + cos(p1.lat) * cos(p2.lat) * sin(dlon/2) * sin(dlon/2)
    c = 2 * atan2(sqrt(a), sqrt(1-a));
    return c * R;
    
class Entry: 
    def __init__(self, la, lo, sp, hd, bu, w):
        self.lat = float(la)
        self.lon = float(lo)
        self.speed = float(sp)
        self.hdg = float(hd)
        self.but = float(bu)
        self.weight = float(w)

    def print(self):
        print("%f %f %f %f %f %f" % (self.lat, self.lon, self.speed, self.hdg, self.but, self.weight))

entries = []

for line in sys.stdin:
    tok = re.split("\s+", line)
    m = {}
    for i in range(0, len(tok) - 1, 2):
        m[tok[i]] = tok[i + 1]
    entries.append(Entry(m["lat"], m["lon"], m["speed"], m["hdg"], m["but"], 1))

entries.sort(key = lambda x : (x.lat, x.hdg, x.lon))

radius = 20
done = False
while not done: 
    print(entries.__len__())
    newents = [];
    avgE = None 
    for e in entries:
        if (avgE == None):
            avgE = copy.copy(e)
            firstE = copy.copy(e)
        elif distance(firstE, e) < radius and math.fabs(hdgDiff(firstE.hdg, e.hdg)) < 45:
            avgE.lat = (avgE.lat * avgE.weight + e.lat) / (avgE.weight + 1)
            avgE.lon = (avgE.lon * avgE.weight + e.lon) / (avgE.weight + 1)
            avgE.speed = (avgE.speed * avgE.weight + e.speed) / (avgE.weight + 1)
            avgE.hdg = (avgE.hdg * avgE.weight + e.hdg) / (avgE.weight + 1)
            avgE.but = (int)(avgE.but) | (int)(e.but);
            avgE.weight = avgE.weight + 1
        else:
            newents.append(e)
        if math.fabs(firstE.lat - e.lat) * 60 * 1852  > radius:
            newents.append(avgE)
            avgE = copy.copy(e)
            firstE = copy.copy(e)
    if avgE != None:
        newents.append(avgE)

    if (newents.__len__() == entries.__len__()):
        done = True
    entries = newents

#for e in entries:
#    e.print()


    