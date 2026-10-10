package com.wesley.flippet;

/** Regression tests of care outcomes, independent of Android or polling rate. */
public final class PetRulesTest {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++; if (!value) throw new AssertionError(why);
    }
    private static void near(double a, double b, String why) {
        check(Math.abs(a - b) < 0.00001, why + ": " + a + " != " + b);
    }
    private static PetRules.Result run(double[] m, boolean sleep, boolean sick, boolean survival, double neglect, double seconds) {
        return PetRules.advance(m, sleep, sick, survival, neglect, seconds);
    }
    public static void main(String[] args) {
        double[] fresh = {70,70,80,75};
        PetRules.Result r = run(fresh,false,false,true,0,18000);
        check(!r.dead,"food empties at five hours; death comes later"); near(r.neglect,0,"grace begins at crossing");
        r = run(fresh,false,false,true,0,18899);
        check(!r.dead,"survives until whole grace elapses"); near(r.neglect,899,"actual empty time only");
        r = run(fresh,false,false,true,0,18900);
        check(r.dead && r.cause==0,"hunger death at 5h15"); near(r.advanced,18900,"stops at death");
        PetRules.Result late = run(fresh,false,false,true,0,7*86400);
        near(late.advanced,r.advanced,"week away has same death time");
        for(int i=0;i<4;i++) near(late.meters[i],r.meters[i],"dead meters frozen");
        r=run(fresh,false,true,true,0,4*3600);
        double[] meters=fresh.clone(); double neglect=0; PetRules.Result small=null;
        for(int i=0;i<2880;i++) {
            small=run(meters,false,true,true,neglect,5);meters=small.meters;neglect=small.neglect;if(small.dead)break;
        }
        check(r.dead==small.dead,"polling does not change survival");
        for(int i=0;i<4;i++) near(r.meters[i],small.meters[i],"polling invariant");
        r=run(fresh,false,true,false,0,30*86400);
        check(!r.dead,"gentle survives a month away");near(r.neglect,0,"no hidden gentle death timer");
        r=run(fresh,false,false,false,0,4*3600);near(r.meters[0],56,"quarter-speed gentle decay");
        r=run(new double[]{50,50,0,50},true,false,true,880,60);
        check(!r.dead && r.meters[2]>0,"sleep rescues zero vitality");near(r.neglect,0,"recovery resets neglect");
        r=run(new double[]{0,50,0,50},true,false,true,880,60);
        check(r.dead && r.cause==0,"sleep cannot rescue starvation");near(r.advanced,20,"existing grace preserved");
        r=run(new double[]{50,0,50,50},false,false,true,60,60);
        check(!r.dead && r.cause==1,"empty joy tracked");near(r.neglect,120,"grace persists");
        r=run(fresh,false,true,true,0,3600);near(r.meters[2],64,"double sick vitality loss");near(r.meters[3],57,"double sick salt loss");
        r=run(fresh,true,true,true,0,3600);near(r.meters[2],93,"sleep recovery not doubled");
        near(fresh[0],70,"input not mutated");
        System.out.println("PetRules: "+checks+" checks passed");
    }
}
