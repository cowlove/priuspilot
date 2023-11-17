#!/usr/bin/python

import sys
f = open(sys.argv[2], "r");
line = f.readline();
words = line.split();
print (words.index(sys.argv[1]) + 2) 

    