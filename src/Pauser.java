

class Pauser {
	boolean paused = false;
	int step = 0;
	
	synchronized void step(int s) { 
		paused = false;
		step = s;
		this.notify();
	}
	synchronized void setPaused(boolean p) { 
		paused = p;
		this.notify();
	}
	synchronized public void togglePaused() {
		paused = !paused;
		this.notify();
	}
	synchronized void checkPaused() { 
		while(paused) {
			try {
				this.wait();
			} catch (InterruptedException e) {}
		}
		if (step > 0 && --step == 0) 
			paused = true;
	}
}
