import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;


class MyCanvas extends JComponent {
	private static final long serialVersionUID = 1L;
	BufferedImage image;

    MyCanvas(BufferedImage i) {
        image = i;
    }

    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.drawImage(image, 0, 0, this);
        //g2.finalize();
    }
}

class MyPanel extends JPanel implements MouseMotionListener, MouseListener, ActionListener, KeyListener {
	//private static final long serialVersionUID = 3580688315933254868L;

	JTextField   typingArea = new JTextField();
	Box box = null;
	JComboBox<String> cb = new JComboBox<String>();
	
	FrameProcessor fp = null;

	JButton addButton(String s) { 
		JButton b = new JButton(s);
		b.addActionListener(this);
		b.setPreferredSize(new Dimension(120, 40));
		b.setAlignmentX(Component.CENTER_ALIGNMENT);
		//box.add(b);
		return b;
	}
	JButton butArm = null, butRec = null, butLock = null;
	
	int width, height;
	MyPanel(FrameProcessor f, int w, int h) {
		fp = f;
		width = w; 
		height = h;
		Box bv = Box.createHorizontalBox();
		add(bv);
		Box bl = Box.createVerticalBox(), br = Box.createVerticalBox();
		bv.add(bl);
		bv.add(br);
		//box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		this.addKeyListener(this);

		typingArea.addKeyListener(this);
	    bl.add(typingArea);
		bl.add(butRec = addButton("RECORD"));
		bl.add(Box.createRigidArea(new Dimension(0,15)));
		bl.add(butArm = addButton("ARM"));
		bl.add(Box.createRigidArea(new Dimension(0,15)));
		bl.add(cb);
		cb.addActionListener(this);
		bl.add(addButton("INCREASE"));
		bl.add(addButton("DECREASE"));

		//addButton("EXIT");
		//cb.addItem(new String("foo"));
		//cb.addItem(new String("bar"));
		

		br.add(butLock = addButton("TF LOCK"));
		br.add(Box.createVerticalGlue());
		br.add(addButton("CC ARM"));
		br.add(Box.createVerticalGlue());
		br.add(addButton("CC UP"));
		br.add(Box.createVerticalGlue());
		br.add(addButton("CC DOWN"));
		typingArea.requestFocus();
	
	}
	
	@Override
	public void actionPerformed(ActionEvent ae) {
		fp.actionPerformed(ae);
		typingArea.requestFocus();
		typingArea.setText("");
	}
	@Override
	public void keyPressed(KeyEvent arg0) {
		// TODO Auto-generated method stub
		fp.keyPressed(arg0.getKeyCode());
		
	}
	@Override
	public void keyReleased(KeyEvent arg0) {
		// TODO Auto-generated method stub		
	}
	@Override
	public void keyTyped(KeyEvent arg0) {
		// TODO Auto-generated method stub		
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub
		int x = arg0.getX();
		int y = arg0.getY();
		
		x = x * fp.width / width;
		y = y * fp.height / height;
		
		fp.onMouseClick(x, y, arg0.getClickCount());
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated metqhod stub
		fp.onMouseReleased();
	}

	@Override
	public void mouseDragged(MouseEvent arg0) {
		// TODO Auto-generated method stub
		int x = arg0.getX() -6;
		int y = arg0.getY() - 26;
		
		x = x * fp.width / width;
		y = y * fp.height / height;
		
		fp.onMouseDragged(x, y);
	}

	@Override
	public void mouseMoved(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

}



class BufferedImageDisplay {
    public JFrame frame = new JFrame();
    public BufferedImage image;
    int width, height;
    int count = 0;
    Graphics2D g2 = null;
    int textrow = 0;
	public int rescale = 1;

    int clamp(int x) { 
		if (x < 0) return 0;
		if (x > 255) return 255; 
		return x;
	}

	int fontSize = 20;
    void writeText(String s) {
        //g2.setColor(Color.blue);
        g2.drawString(s, 10, ++textrow * (fontSize + 1) * rescale);
    }
    static int nextX = 0;
    static int nextY = 0;
    JComponent canv;
    int xpos, ypos, bx, by;
    
    public void init(int w, int h, int _bx, int _by, int type) {
        width = w;
        height = h;
		bx = _bx;
		by = _by;
        xpos = nextX;
        ypos = nextY;
        nextY += h + by;
        if (nextY > 600) { 
        	nextX += w + bx;
        	nextY = 0;
        }
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBounds(xpos, ypos, width + bx, height + by);
        image = new BufferedImage(width, height, type);
        canv = new MyCanvas(image);
        frame.getContentPane().add(canv, BorderLayout.CENTER);
        frame.pack();
		frame.setVisible(true);
        g2 = image.createGraphics();
    }
    public BufferedImageDisplay(int w, int h, int bx, int by, int type) {
    	init(w, h, bx, by, type);    	
    }
    public BufferedImageDisplay(int w, int h, int type) {
    	init(w, h, 0, 0, type);
    }
    void setData(int x, int y, int w, int h, byte[] d) { 
    	image.getWritableTile(0, 0).setDataElements(x, y, w, h, d);
//		redraw(false);		
    }
    void setData(Rectangle r, byte[] d) { 
    	image.getWritableTile(0, 0).setDataElements(r.x, r.y, r.width, r.height, d);
//		redraw(false);		
    }
    public void done(Rectangle zoom) { 
      	for(Annotation a : annotations) {
            g2.setColor(a.c);
            g2.drawString(a.txt, a.x, a.y);
    	}
      	annotations.clear();
      	redraw();
    }
    public void redraw() {
        frame.repaint();
        textrow = 0;
    }

    public void rectangleAtPixel(Color c, String label, int px, int py, int w, int h, boolean fill) { 
        g2.setColor(c);
        if (fill)  
        	g2.fill(new Rectangle(px, py, w, h));
        else
        	g2.draw(new Rectangle(px, py, w, h));
        g2.setColor(Color.black);
        g2.drawString(label, px + 2, py + h);
    }
    public void text(String s, double x, double y) { 
        int px = (int)(x * width);
        int py = (int) (y * height);
        g2.drawString(s, px + 2, py);
    	
    }
    public void rectangle(Color c, String label, double x, double y, double w, double h) {
    	rectangle(c, label, x, y, w, h, true);
    }
    public void rectangle(Color c, String label, double x, double y, double w, double h, boolean fill) {
        int px = (int) ((x - w / 2) * width);
        int py = (int) (y * height);
        rectangleAtPixel(c, label, px, py, (int)(w * width), (int)(h * height), fill);
    }

	public void draw(Color c, Shape s) { 
		g2.setColor(c);
		g2.draw(s);
	}
	public void clearBackground(Color c) {
		g2.setColor(c);
		g2.fill(new Rectangle(0, 0, width, height));
	}
	
	class Annotation { 
		int x, y;
		Color c;
		String txt;
		Annotation(int x, int y, Color c, String txt) { 
			this.x = x; this.y = y; this.c = c; this.txt = txt;
		}
	}
	ArrayList<Annotation> annotations = new ArrayList<Annotation>();
	public void annotate(int x, int y, Color c, String txt) { 
		annotations.add(new Annotation(x, y, c, txt));
	}
	public void setData(int x, int y, int w, int h,
			int[] d) {
    	image.getWritableTile(0, 0).setDataElements(x, y, w, h, d);		
	}
}

class BufferedImageDisplayWithInputs extends BufferedImageDisplay { 
	MyPanel panel;
	public BufferedImageDisplayWithInputs(FrameProcessor fp, int w, int h) {
    	super(w, h, 280, 40, BufferedImage.TYPE_3BYTE_BGR);
    	panel = new MyPanel(fp, w, h);
        frame.setBounds(xpos,ypos, width + bx, height + by);
        frame.getContentPane().add(panel, BorderLayout.EAST);
		frame.addMouseListener(panel);
		frame.addMouseMotionListener(panel);
	    frame.setVisible(true);
	    g2 = image.createGraphics();
		panel.typingArea.requestFocus();
    }
    void addKeyListener(KeyListener l) { 
    	frame.addKeyListener(l);
    }

    public void redraw(boolean takeFocus) {
        super.redraw();
        if (takeFocus) {
        	frame.setVisible(true);
			if (!panel.cb.isPopupVisible())
        		panel.typingArea.requestFocus();
        }
        textrow = 0;
    }
 
}

class BufferedImageDebugDisplay extends BufferedImageDisplay { 
	byte [] rgb = null;
	final int bpp = 3;
	public BufferedImageDebugDisplay(int w, int h) {
		super(w, h, 5, 60, BufferedImage.TYPE_3BYTE_BGR);
		rgb = new byte[w * h * bpp];
	}
	void setPixel(int x, int y, int argb) {
		final int i = (y * width + x) * bpp;
		final int c = argb;
		rgb[i] = (byte)((c & 0xff0000) >> 16);
		rgb[i+1]  = (byte)((c & 0xff00) >> 8);
		rgb[i+2] = (byte)(c & 0xff);
	}
	void setPixelGrey(int x, int y, int grey) {
		final int i = (y * width + x) * bpp;
		final int c = grey;
		rgb[i] = rgb[i+1] = rgb[i+2] = (byte)(c & 0xff);
	}
	void display() { 
		WritableRaster r = image.getWritableTile(0, 0);
		r.setDataElements(0, 0, width, height, rgb);
		redraw();
	
	}
}