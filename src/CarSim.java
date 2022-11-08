

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.BasicStroke;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;


class CarSim { 
    long ms = 0;
    int width, height; 
    Point van;
    int ll, rl;
    int linew = 2;
    CarSim(int w, int h) { 
        width =w ;
        height = h;
        van = new Point(width / 2, (int)(height * .2));
        ll = (int)(width * -0.1);
        rl = (int)(width * 1.1);
    }

    double steer = 0.0;
    double ang = 0, pos = 0;

    void setSteer(double s) { 
        steer = s;
    }

    ArrayDeque<Double> delaySteer = new ArrayDeque<Double>();
    ArrayDeque<Double> delayAng = new ArrayDeque<Double>();

    double clamp(double x, double l) {
        if (Math.abs(x) > l) { 
            if (x < 0) { 
                x  = Math.sqrt(-x / l) * -l;
            } else { 
                x = Math.sqrt(x / l) * l;
            }
        }
        return x;
    }

    ByteBuffer getFrame(long ms) { 
        delaySteer.addLast(steer);
        if (delaySteer.size() > 15) {
            double s = delaySteer.removeFirst();
            if (Math.abs(s) < .1) 
                s = 0;
            ang -= s * 0.60;

        }
        delayAng.addLast(ang);
        if (delayAng.size() > 15) { 
            pos += delayAng.removeFirst() * 0.45;
        }

        ang = clamp(ang, 60);
        pos = clamp(pos, 150);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        Color bg = Color.DARK_GRAY;
        g2.setColor(bg);
        g2.fillRect(0, 0, width, height);
        
      
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(linew));
        int x1 = (int)(van.x + ang);
        g2.drawLine(x1, van.y, (int)(ll + pos), height);
        g2.drawLine(x1, van.y, (int)(ll + pos + linew * 4), height);
        g2.drawLine(x1, van.y, (int)(rl + pos), height);
        g2.drawLine(x1, van.y, (int)(rl + pos + linew * 4), height);
        g2.setColor(bg);
        g2.fillRect(0, 0, width, (int)(van.y * 2));
        int y1 = (int)((ms % 3000) * height / 3000);
        g2.fillRect(0, y1, width/2, y1/2);
        
        ByteBuffer bb = ByteBuffer.allocate(height * width * 2);

        for(int y = 0; y < height; y++) { 
            for(int x = 0; x < width; x += 2) { 
                int color = image.getRGB(x, y);

                int R = color >> 16 & 0xff;
                int G = color >> 8 & 0xff;
                int B = color & 0xff;
                
                int Y1 = (int)(R *  .299000 + G *  .587000 + B *  0.114000);
                int U = (int)(R * -.168736 + G * -.331264 + B *  0.500000 + 128);
                int V = (int)(R *  .500000 + G * -.418688 + B * -0.081312 + 128);



                color = image.getRGB(x + 1, y);
                int Y2 = (int)(R *  .299000 + G *  .587000 + B *  0.114000);

                bb.put((y * width + x) * 2, (byte)Y1);
                bb.put((y * width + x) * 2 + 1, (byte)V);
                bb.put((y * width + x) * 2 + 2, (byte)Y2);
                bb.put((y * width + x) * 2 + 3, (byte)U);

            }
        }
        return bb;
    }
}

