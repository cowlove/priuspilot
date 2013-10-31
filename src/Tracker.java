import java.awt.Point;


// getPotentialTargets, draw them or whatever
// once on is selected, call lockTarget()
//
// check getLockedTarget() to draw it and check on validity of lock
// 
// 
// 

class Tracker {
	class Target {
	}
	class SearchArea {
		Point ul, ur, ll, lr; 
	}
	void setSearchArea(int w, int h, SearchArea sa) { 
	}
	void processFrame(byte [] pic) {
	}
	Target [] getPotentialTargets() {
		return null;
	}
	void lockNearestTarget(Target t) {
	}
	Target getLockedTarget() {
		return null;
	}
	public boolean enabled = false;
	PidControl pid = null;
}
