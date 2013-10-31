
import java.io.*;
import javax.sound.midi.*;

import sun.audio.*;


import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

class Tone {

    public static void go() throws LineUnavailableException {
        final AudioFormat af =
            new AudioFormat(Note.SAMPLE_RATE, 8, 1, true, true);
        SourceDataLine line = AudioSystem.getSourceDataLine(af);
        line.open(af, Note.SAMPLE_RATE);
        line.start();
        for  (Note n : Note.values()) {
            play(line, n, 500);
            play(line, Note.REST, 10);
        }
        line.drain();
        line.close();
    }

    private static void play(SourceDataLine line, Note note, int ms) {
        ms = Math.min(ms, Note.SECONDS * 1000);
        int length = Note.SAMPLE_RATE * ms / 1000;
        int count = line.write(note.data(), 0, length);
    }
}

enum Note {

    REST, A4, A4$, B4, C4, C4$, D4, D4$, E4, F4, F4$, G4, G4$, A5;
    public static final int SAMPLE_RATE = 16 * 1024; // ~16KHz
    public static final int SECONDS = 2;
    private byte[] sin = new byte[SECONDS * SAMPLE_RATE];

    Note() {
        int n = this.ordinal();
        if (n > 0) {
            double exp = ((double) n - 1) / 12d;
            double f = 440d * Math.pow(2d, exp);
            for (int i = 0; i < sin.length; i++) {
                double period = (double)SAMPLE_RATE / f;
                double angle = 2.0 * Math.PI * i / period;
                sin[i] = (byte)(Math.sin(angle) * 127f);
            }
        }
    }

    public byte[] data() {
        return sin;
    }
}

class AdvisorySounds extends Thread {

	Synthesizer synth = null;
    MidiChannel channels [] = null;
    public int volume = 0;
    
    
    class Chord { 
    	int [] notes;
    	Chord(int []n) { notes = n.clone(); }
    	Chord Transpose(int n) { 
    		Chord r = new Chord(notes);
    		for(int i = 0; i < r.notes.length; i++) 
    			r.notes[i] += n;    
    		return r;
    	}
    	int getNote(double octave) { 
    		int oct = (int)octave;
    		int rem = (int)(notes.length * (octave - oct));
    		return notes[rem] + oct * 12;
    	}
    }
    
    Chord Cm7 = new Chord(new int[]{0, 3, 7, 10});
    Chord Dm7 = Cm7.Transpose(2);
    Chord CmScale = new Chord(new int[]{0,2,3,5,7,9,11});
        
    class ChordArray { 
        Chord []chords;
        int bpm = 16, beats = 0;
    	ChordArray(Chord []c) { chords = c; }
    	int getNote(double n) {
    		return chords[(beats++ / bpm) % chords.length].getNote(n);
    	}
    }

    ChordArray chords = new ChordArray(new Chord[]{Cm7});
    
    @Override
    public void run() {
		int count = 0;
		while(true) {
			double n;
			count++;
			synchronized(this) { 
				n = level;
				level = -1;
			}
			try {
				//Tone.go();
				if (volume > 0 && n >= 0) {
					if (synth == null)
						init();
					int note = 0, note2 = 0;
			        channels[0].controlChange(7, volume * 12);
			        channels[1].controlChange(7, volume * 12);
		        channels[2].controlChange(7, Math.abs(((count + 12 ) % 24) -12) * 11);
			        channels[3].controlChange(7, Math.abs(((count + 12 ) % 24) -12) * 11);
			        channels[4].controlChange(7, Math.abs(((count + 12 ) % 24) -12) * 11);
				    note = 40 + chords.getNote(n * 4);
			        note2 = 40 + chords.getNote(2);
				
			        channels[0].noteOn(40 + chords.getNote(n * 4), (int)(70 + n * 20));
			        //channels[0].noteOn(40 + chords.getNote(n * 4 + 0.4), (int)(70 + n * 20));
			        if (false && count % 24 == 0) { 
						channels[2].allNotesOff();
						channels[3].allNotesOff();
						channels[4].allNotesOff();
						double b = count % 48 == 0 ? 0.5 : 2.0;
						channels[2].noteOn(40 + chords.getNote(b), (int)(70 + n * 20));
						channels[3].noteOn(40 + chords.getNote(b+.5), (int)(70 + n * 20));
						channels[4].noteOn(40 + chords.getNote(b+.2), (int)(70 + n * 20));
			        }
					Thread.sleep(250);
					channels[0].allNotesOff();
				} else 
					Thread.sleep(100);
				
				
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
		}
	}
	
	synchronized void setAlertLevel(double n) { 
    	level = n;
    }
    double level = 0;
    void init() { 
        try {
			synth = MidiSystem.getSynthesizer();
	        synth.open();
	
	        Soundbank soundbank = synth.getDefaultSoundbank();
	    	Instrument[] aInstruments = soundbank.getInstruments();
			for (int i = 0; i < aInstruments.length && false; i++)
			{
				System.out.print("" + i + ":[" + aInstruments[i].getPatch().getBank() + ", " +
					aInstruments[i].getPatch().getProgram() + "] " +
					aInstruments[i].getName() + "\n");
			}

	        
	        Instrument[] instr = synth.getDefaultSoundbank().getInstruments(); 
	        //synth.loadInstrument(instr[10]);  // Bottle Blow 
	        channels = synth.getChannels();
	        int patch = 62; // 52 choir 53 oohs 54 synth voice  48-51 strings, 56 trumpet, 61 brass, 62-63 synth brass 
	        channels[2].programChange(patch);
	        channels[3].programChange(patch);
	        channels[4].programChange(patch);
	    	        
		} catch (MidiUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    AdvisorySounds() { 
    	this.start();
    }
    
}