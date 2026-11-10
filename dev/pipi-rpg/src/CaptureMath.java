package dev.pipi.life;

/** Design-sheet capture formula (Pokemon gen-5 style).
 * a = (3*M - 2*H) * rate * ball / (3*M) with M max HP, H current HP,
 * rate the species capture rate and ball 1 / 1.5 / 2 for basic / iron / diamond frames.
 * If a >= 255 the capture succeeds immediately. Otherwise b = 65536 / (255/a)^(3/16)
 * and exactly three checks are made (the first plus two shakes): each draws a random
 * integer 0..65535 and passes if below b. All three passing means captured; failing at
 * check i breaks out after i shakes.
 */
final class CaptureMath {
    static double ballFactor(int tier) { return new double[]{1,1.5,2}[Math.clamp(tier,0,2)]; }
    static double a(double hp,double maximum,double speciesRate,int tier) {
        if(!Double.isFinite(hp)||!Double.isFinite(maximum)||!Double.isFinite(speciesRate)||maximum<=0||speciesRate<=0)return 0;
        return (3*maximum-2*hp)*speciesRate*ballFactor(tier)/(3*maximum);
    }
    static double shakeBound(double aValue) { return aValue<=0?0:65536/Math.pow(255/aValue,3.0/16.0); }
    static double probability(double hp,double maximum,double speciesRate,int tier) {
        double a=a(hp,maximum,speciesRate,tier);
        if(a>=255)return 1;
        double p=Math.clamp(shakeBound(a)/65536,0,1);
        return p*p*p;
    }
    static int outcome(double hp,double maximum,double speciesRate,int tier,int... rolls) {
        if(rolls.length!=3)throw new IllegalArgumentException("Three rolls required");
        double a=a(hp,maximum,speciesRate,tier);
        if(a>=255)return 3;
        double b=shakeBound(a);
        for(int i=0;i<3;i++)if(rolls[i]>=b)return i;
        return 3;
    }
    static double throwSpeed(long ticks) { return .65+1.15*Math.clamp(ticks/20.0,0,1); }
    static double clubDamage(int tier, boolean critical) {
        return new double[]{3,5,7}[Math.clamp(tier,0,2)]*(critical?1.5:1);
    }
    static double weakenedHealth(double hp,double max,int tier) { return Math.max(0,hp-clubDamage(tier,false)); }
}
