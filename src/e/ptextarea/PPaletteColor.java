package e.ptextarea;

import java.awt.*;
import java.awt.color.*;

public enum PPaletteColor {
    /*
     * Data for color palette optimization.
     *
     * These numbers are completely arbitrary decisions, uninformed by the experts
     * at crayola.  These colors are defined as boxes within the CIE L*a*b* color
     * space -- while they're not fully inclusive, they are "safe" in that anywhere
     * within a given region is guaranteed to be the expected color.  The data here
     * are endpoints in each dimension of CIELAB, from which the 8 corners of the
     * region are created to test.
     */
    AQUA        (40.0f,  60.0f, -100.0f, -80.0f,  -10.0f,  20.0f),
    BLACK       ( 0.0f,  30.0f,    0.0f,   0.0f,    0.0f,   0.0f),
    BLUE        (25.0f,  35.0f, -100.0f,   0.0f, -100.0f, -50.0f),
    BROWN       (30.0f,  60.0f,   30.0f,  50.0f,   70.0f, 100.0f),
    CYAN        (50.0f,  65.0f, -100.0f, -30.0f, -100.0f, -50.0f),
    DARK_BLUE   ( 0.0f,  20.0f,  -40.0f,  50.0f, -100.0f, -60.0f),
    DARK_GREEN  (20.0f,  35.0f, -100.0f, -70.0f,   60.0f, 100.0f),
    DARK_GRAY   (20.0f,  40.0f,    0.0f,   0.0f,    0.0f,   0.0f), 
    DARK_RED    (10.0f,  40.0f,   90.0f, 100.0f,   70.0f, 100.0f), 
    GREEN       (15.0f,  40.0f, -100.0f, -80.0f,   80.0f, 100.0f), 
    GRAY        (35.0f,  60.0f,    0.0f,   0.0f,    0.0f,   0.0f), 
    LIGHT_BLUE  (40.0f,  50.0f, -100.0f,   0.0f, -100.0f, -60.0f), 
    LIGHT_BROWN (60.0f,  75.0f,   30.0f,  50.0f,   80.0f, 100.0f), 
    LIGHT_GREEN (80.0f,  90.0f, -100.0f, -70.0f,   70.0f, 100.0f), 
    LIGHT_GRAY  (50.0f,  80.0f,    0.0f,   0.0f,    0.0f,   0.0f), 
    LIGHT_RED   (55.0f,  65.0f,   80.0f,  90.0f,   75.0f, 100.0f), 
    MAGENTA     (40.0f,  55.0f,   90.0f, 100.0f,  -50.0f,   0.0f), 
    ORANGE      (65.0f,  80.0f,   20.0f,  65.0f,   90.0f, 100.0f), 
    PURPLE      (35.0f,  45.0f,   85.0f, 100.0f,  -90.0f, -80.0f), 
    RED         (40.0f,  50.0f,   80.0f, 100.0f,   75.0f, 100.0f), 
    VIOLET      (70.0f,  95.0f,   90.0f, 100.0f, -100.0f,   0.0f), 
    WHITE       (75.0f, 100.0f,    0.0f,   0.0f,    0.0f,   0.0f), 
    YELLOW      (90.0f, 100.0f,    5.0f,  15.0f,   92.5f, 105.0f),
    ;

    private float[][] extentPointsLab;

    private static final float Eps = 216.f/24389.f;
    private static final float K = 24389.f/27.f;
    private static final float WhiteXYZ[] = 
        Color.WHITE.getColorSpace().toCIEXYZ(
            Color.WHITE.getColorComponents(null));

    private PPaletteColor(float r0, float r1, float r2, float r3, float r4, float r5)
    {
        extentPointsLab = new float[][] {
            { r0, r2, r4 },
            { r0, r2, r5 },
            { r0, r3, r4 },
            { r0, r3, r5 },
            { r1, r2, r4 },
            { r1, r2, r5 },
            { r1, r3, r4 },
            { r1, r3, r5 },
        };
    }

    public Color getColor(Color bg) 
    {
        float[] bgLab = new float[3];
        convertColorToLab(bg, bgLab);

        float maxDist = 0.0f;
        float[] maxPoint = null;

        for (float[] extentPoint : extentPointsLab) {
            float dist = getLabDistance(bgLab, extentPoint);
            if (dist > maxDist) {
                maxDist = dist;
                maxPoint = extentPoint;
            }
        }

        float[] bestLab = new float[3];
        extendLab(bgLab, maxPoint, bestLab);

        Color color = convertLabToColor(bestLab, bg.getColorSpace());

        /*
        System.out.println("PPaletteColor:getColor:" +
                           "\n\tfg=" + color + 
                           "\n\tbestDistance=" + maxDist);
        */

        return color;
    }

    private float getLabDistance(float[] bgLab, float[] fgLab)
    {
        float dL, da, db;
        dL = fgLab[0] - bgLab[0];
        da = fgLab[1] - bgLab[1];
        db = fgLab[2] - bgLab[2];
        return (float) Math.sqrt(dL*dL + da*da + db*db);
    }

    private void extendLab(float[] bgLab, float[] lab, float[] labOut)
    {
        /*
         * If the luminosity distance is really short, extend the vector further
         * out.  This may push it outside the bounds of the region that a color
         * is specified in, but it keeps things readable when the background and
         * foreground are really close.
         */
        float ld = Math.abs(bgLab[0] - lab[0]);
        float cd = (float) Math.sqrt(Math.pow(bgLab[1] - lab[1], 2) + 
                                     Math.pow(bgLab[2] - lab[2], 2));
        if ((ld < 10.0f) && (cd < 60.0f)) {
            labOut[0] = bgLab[0] + ((lab[0] - bgLab[0]) * 4.0f);
            labOut[1] = bgLab[1] + ((lab[1] - bgLab[1]) * 1.5f);
            labOut[2] = bgLab[2] + ((lab[2] - bgLab[2]) * 1.5f);
        } else {
            labOut[0] = lab[0];
            labOut[1] = lab[1];
            labOut[2] = lab[2];
        }
    }

    private void convertColorToLab(Color color, float []labOut)
    {
        float xyz[] = color.getColorSpace().toCIEXYZ(color.getColorComponents(null));

        // XYZ to Lab
        float fx, fy, fz, xr, yr, zr;
        xr = xyz[0]/WhiteXYZ[0];
        yr = xyz[1]/WhiteXYZ[1];
        zr = xyz[2]/WhiteXYZ[2];

        if (xr > Eps)
            fx =  (float) Math.pow(xr, 1/3.);
        else
            fx = (float) ((K * xr + 16.) / 116.);

        if (yr > Eps)
            fy =  (float) Math.pow(yr, 1/3.);
        else
            fy = (float) ((K * yr + 16.) / 116.);

        if (zr > Eps)
            fz =  (float) Math.pow(zr, 1/3.);
        else
            fz = (float) ((K * zr + 16.) / 116);

        labOut[0] = (116 * fy) - 16;
        labOut[1] = 500*(fx-fy);
        labOut[2] = 200*(fy-fz);
    }

    private Color convertLabToColor(float[] lab, ColorSpace cspace)
    {
        // Lab to XYZ
        float fx, fy, fz, xr, yr, zr;

        if (lab[0] > K * Eps)
            yr = (float) Math.pow((lab[0] + 16.) / 116., 3);
        else
            yr = lab[0] / K;

        if (yr > Eps)
            fy = (float) ((lab[0] + 16.) / 116.);
        else
            fy = (float) ((K * yr + 16.) / 116.);

        fx = (lab[1] / 500) + fy;
        xr = (float) Math.pow(fx, 3);
        if (xr <= Eps)
            xr = (float) ((116. * fx - 16.) / K);

        fz = fy - (lab[2] / 200);
        zr = (float) Math.pow(fz, 3);
        if (zr <= Eps)
            zr = (float) ((116. * fz - 16.) / K);

        float[] xyz = new float[] { xr*WhiteXYZ[0], yr*WhiteXYZ[1], zr*WhiteXYZ[2] };
        return new Color(cspace, cspace.fromCIEXYZ(xyz), 1.0f);
    }

    public static void main(String[] args) 
    {
       System.out.println("WHITE XYZ=[" + WhiteXYZ[0] + 
                          ", " + WhiteXYZ[1] + 
                          ", " + WhiteXYZ[2] + "]");
                          
       Color[] bgList = {
           Color.BLACK,
           Color.BLUE,
           Color.CYAN,
           Color.DARK_GRAY,
           Color.GRAY,
           Color.GREEN,
           Color.LIGHT_GRAY,
           Color.MAGENTA,
           Color.ORANGE,
           Color.PINK,
           Color.RED,
           Color.WHITE,
           Color.YELLOW,
       };
          
       for (Color bg : bgList) {
           System.out.println("Trying BG Color: " + bg);
           for (PPaletteColor palette : PPaletteColor.values()) {
               System.out.println("   Trying Palette: " + palette + 
                                  ", FG Color: " + palette.getColor(bg));
           }
           System.out.println("");
       }

       for (PPaletteColor palette : PPaletteColor.values()) {
           System.out.println("Trying Palette: " + palette);
           for (Color bg : bgList) {
               System.out.println("   BG: " + bg + ", FG: " + palette.getColor(bg));
           }
           System.out.println("");
       }
    }
}
