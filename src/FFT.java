//package math;

/******************************************************************************
 *  Compilation:  javac FFT.java
 *  Execution:    java FFT n
 *  Dependencies: Complex.java
 *
 *  Compute the FFT and inverse FFT of a length n complex sequence
 *  using the radix 2 Cooley-Tukey algorithm.

 *  Bare bones implementation that runs in O(n log n) time. Our goal
 *  is to optimize the clarity of the code, rather than performance.
 *
 *  Limitations
 *  -----------
 *   -  assumes n is a power of 2
 *
 *   -  not the most memory efficient algorithm (because it uses
 *      an object type for representing complex numbers and because
 *      it re-allocates memory for the subarray, instead of doing
 *      in-place or reusing a single temporary array)
 *  
 *  For an in-place radix 2 Cooley-Tukey FFT, see
 *  https://introcs.cs.princeton.edu/java/97data/InplaceFFT.java.html
 *
 ******************************************************************************/


/******************************************************************************
 *  Compilation:  javac Complex.java
 *  Execution:    java Complex
 *
 *  Data type for complex numbers.
 *
 *  The data type is "immutable" so once you create and initialize
 *  a Complex object, you cannot change it. The "final" keyword
 *  when declaring re and im enforces this rule, making it a
 *  compile-time error to change the .re or .im instance variables after
 *  they've been initialized.
 *
 *  % java Complex
 *  a            = 5.0 + 6.0i
 *  b            = -3.0 + 4.0i
 *  Re(a)        = 5.0
 *  Im(a)        = 6.0
 *  b + a        = 2.0 + 10.0i
 *  a - b        = 8.0 + 2.0i
 *  a * b        = -39.0 + 2.0i
 *  b * a        = -39.0 + 2.0i
 *  a / b        = 0.36 - 1.52i
 *  (a / b) * b  = 5.0 + 6.0i
 *  conj(a)      = 5.0 - 6.0i
 *  |a|          = 7.810249675906654
 *  tan(a)       = -6.685231390246571E-6 + 1.0000103108981198i
 *
 ******************************************************************************/


class StdOut { 
	static public int println(String s) { return 0; }
	static public int println() { return 0; } 
	static public int println(Complex c) { return 0; } 
}
public class FFT {

    // compute the FFT of x[], assuming its length is a power of 2
    public static Complex[] fft(Complex[] x) {
        int n = x.length;

        // base case
        if (n == 1) return new Complex[] { x[0] };

        // radix 2 Cooley-Tukey FFT
        if (n % 2 != 0) {
            throw new IllegalArgumentException("n is not a power of 2");
        }

        // fft of even terms
        Complex[] even = new Complex[n/2];
        for (int k = 0; k < n/2; k++) {
            even[k] = x[2*k];
        }
        Complex[] q = fft(even);

        // fft of odd terms
        Complex[] odd  = even;  // reuse the array
        for (int k = 0; k < n/2; k++) {
            odd[k] = x[2*k + 1];
        }
        Complex[] r = fft(odd);

        // combine
        Complex[] y = new Complex[n];
        for (int k = 0; k < n/2; k++) {
            double kth = -2 * k * Math.PI / n;
            Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
            y[k]       = q[k].plus(wk.times(r[k]));
            y[k + n/2] = q[k].minus(wk.times(r[k]));
        }
        return y;
    }


    // compute the inverse FFT of x[], assuming its length is a power of 2
    public static Complex[] ifft(Complex[] x) {
        int n = x.length;
        Complex[] y = new Complex[n];

        // take conjugate
        for (int i = 0; i < n; i++) {
            y[i] = x[i].conjugate();
        }

        // compute forward FFT
        y = fft(y);

        // take conjugate again
        for (int i = 0; i < n; i++) {
            y[i] = y[i].conjugate();
        }

        // divide by n
        for (int i = 0; i < n; i++) {
            y[i] = y[i].scale(1.0 / n);
        }

        return y;

    }

    // compute the circular convolution of x and y
    public static Complex[] cconvolve(Complex[] x, Complex[] y) {

        // should probably pad x and y with 0s so that they have same length
        // and are powers of 2
        if (x.length != y.length) {
            throw new IllegalArgumentException("Dimensions don't agree");
        }

        int n = x.length;

        // compute FFT of each sequence
        Complex[] a = fft(x);
        Complex[] b = fft(y);

        // point-wise multiply
        Complex[] c = new Complex[n];
        for (int i = 0; i < n; i++) {
            c[i] = a[i].times(b[i]);
        }

        // compute inverse FFT
        return ifft(c);
    }


    // compute the linear convolution of x and y
    public static Complex[] convolve(Complex[] x, Complex[] y) {
        Complex ZERO = new Complex(0, 0);

        Complex[] a = new Complex[2*x.length];
        for (int i = 0;        i <   x.length; i++) a[i] = x[i];
        for (int i = x.length; i < 2*x.length; i++) a[i] = ZERO;

        Complex[] b = new Complex[2*y.length];
        for (int i = 0;        i <   y.length; i++) b[i] = y[i];
        for (int i = y.length; i < 2*y.length; i++) b[i] = ZERO;

        return cconvolve(a, b);
    }

    // display an array of Complex numbers to standard output
    public static void show(Complex[] x, String title) {
        StdOut.println(title);
        StdOut.println("-------------------");
        for (int i = 0; i < x.length; i++) {
            StdOut.println(x[i]);
        }
        StdOut.println();
    }


   /***************************************************************************
    *  Test client and sample execution
    *
    *  % java FFT 4
    *  x
    *  -------------------
    *  -0.03480425839330703
    *  0.07910192950176387
    *  0.7233322451735928
    *  0.1659819820667019
    *
    *  y = fft(x)
    *  -------------------
    *  0.9336118983487516
    *  -0.7581365035668999 + 0.08688005256493803i
    *  0.44344407521182005
    *  -0.7581365035668999 - 0.08688005256493803i
    *
    *  z = ifft(y)
    *  -------------------
    *  -0.03480425839330703
    *  0.07910192950176387 + 2.6599344570851287E-18i
    *  0.7233322451735928
    *  0.1659819820667019 - 2.6599344570851287E-18i
    *
    *  c = cconvolve(x, x)
    *  -------------------
    *  0.5506798633981853
    *  0.23461407150576394 - 4.033186818023279E-18i
    *  -0.016542951108772352
    *  0.10288019294318276 + 4.033186818023279E-18i
    *
    *  d = convolve(x, x)
    *  -------------------
    *  0.001211336402308083 - 3.122502256758253E-17i
    *  -0.005506167987577068 - 5.058885073636224E-17i
    *  -0.044092969479563274 + 2.1934338938072244E-18i
    *  0.10288019294318276 - 3.6147323062478115E-17i
    *  0.5494685269958772 + 3.122502256758253E-17i
    *  0.240120239493341 + 4.655566391833896E-17i
    *  0.02755001837079092 - 2.1934338938072244E-18i
    *  4.01805098805014E-17i
    *
    ***************************************************************************/

    public static void main(String[] args) { 
        int n = Integer.parseInt(args[0]);
        Complex[] x = new Complex[n];

        // original data
        for (int i = 0; i < n; i++) {
            x[i] = new Complex(i, 0);
            x[i] = new Complex(-2*Math.random() + 1, 0);
        }
        show(x, "x");

        // FFT of original data
        Complex[] y = fft(x);
        show(y, "y = fft(x)");

        // take inverse FFT
        Complex[] z = ifft(y);
        show(z, "z = ifft(y)");

        // circular convolution of x with itself
        Complex[] c = cconvolve(x, x);
        show(c, "c = cconvolve(x, x)");

        // linear convolution of x with itself
        Complex[] d = convolve(x, x);
        show(d, "d = convolve(x, x)");
    }

}

