import xml.etree.ElementTree as ET
from xml.etree import ElementTree
from xml.dom import minidom
import csv
import sys
import scipy.io as sio
import datetime
import pathwriter
import planparser
import random

TRAV_TIME_SECONDS = 60

def turnCSVIntoPlans(filename, roads):
	plans = ET.Element("population")
	rawdata = planparser.loadMatData('bin/zhangNYdata.mat')
	locations = rawdata['NodesLocation']

	with open(filename, 'r') as f:
		reader = csv.reader(f)
		for i, trip in enumerate(reader):
			dropoffx, dropoffy = float(trip[7])*1000000, float(trip[8])*1000000
			pickupx, pickupy = float(trip[5])*1000000, float(trip[6])*1000000
			closestpickupnode = planparser.findClosestNode(pickupx, pickupy, locations)
			listoflinks = roads.get(str(closestpickupnode))
			pickuplink = str(random.choice(listoflinks))
			closestdropoffnode = planparser.findClosestNode(dropoffx, dropoffy, locations)
			listoflinks = roads.get(str(closestdropoffnode))
			dropofflink = random.choice(listoflinks)

			if (dropofflink != pickuplink):
				person = ET.SubElement(plans, "person")
				person.set('id', str(i+1))
				plan = ET.SubElement(person, "plan")
				plan.set('selected', 'yes')
			
				hour, minute, second = int(trip[2]), int(trip[3]), int(trip[4])
				time = datetime.datetime(100,1,1,hour,minute,second)
				act = ET.SubElement(plan, 'act')
				act.set('type', 'dummy')
				act.set('end_time', str(time.time()))
			
				act.set('link', pickuplink)
				leg = ET.SubElement(plan, 'leg')
				leg.set('mode', 'taxi')
				leg.set('dep_time', str(time.time()))
				leg.set('trav_time', '00:01:00')
				delta = datetime.timedelta(0,TRAV_TIME_SECONDS)
				leg.set('arr_time', str((time + delta).time()))
				route = ET.SubElement(leg, 'route')
				act2 = ET.SubElement(plan, 'act')
				act2.set('type', 'dummy')
			
				act2.set('link', dropofflink)			
				print(i)

	print("Cleaning XML...")
	parsed = pathwriter.cleanXML(plans)
	parsed = parsed[0:22] + '\n<!DOCTYPE population SYSTEM "http://www.matsim.org/files/dtd/population_v5.dtd">' + \
			 parsed[22:]

	print("Writing XML...")
	outfilename = 'res/amod1820plans.xml'
	with open(outfilename, 'w') as f:
		f.write(parsed)

def prepRoadData(filename):
	roads = {}
	with open(filename,'r') as f:
		reader = csv.reader(f)
		for line in reader: 
			start = line[1]
			if roads.get(start) == None:
				roads[start] = [line[0]]
			else:
				roads.get(start).append(line[0])
	return roads


if __name__ == "__main__":
	roads = prepRoadData('bin/roads.csv')
	turnCSVIntoPlans(sys.argv[1], roads)