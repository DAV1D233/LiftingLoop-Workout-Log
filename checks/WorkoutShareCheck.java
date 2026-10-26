import com.fitlog.app.domain.*;
import kotlin.Unit;
import java.util.*;

/** Document pagination and real Gaussian behavior; independent of Android rendering. */
public class WorkoutShareCheck {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static CompletedSet set(int action, String name, int ordinal) { return new CompletedSet(action, name, ordinal, 42.5, 12, 0L); }
    static void rejects(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); } catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        var model = WorkoutShareLayout.INSTANCE;
        var grouped = model.actions(List.of(set(1,"同名动作",3),set(1,"同名动作",1),set(1,"同名动作",2),set(2,"同名动作",1)));
        check(grouped.size()==2, "Same names with different identities must remain separate");
        check(grouped.get(0).getRows().get(0).get(0).getOrdinal()==1, "Sets sorted by ordinal");
        check(grouped.get(0).getRows().get(0).size()==3,"Three resistance chips per row");
        check(model.setText(set(0,"test",1)).equals("42.5kg ×12"),"Weight unit and reps retained");
        var cardio = new CompletedSet(2,"游泳",1,0,30,0L,true,95,750,"计次","自由泳",25);
        var line = model.setText(cardio);
        check(line.contains("1 分 35 秒") && line.contains("750 米") && line.contains("30 次") && line.contains("自由泳") && line.contains("25 米"), "All cardio fields retained");
        check(model.pages(List.of(),100).size()==1,"Empty summary renders a page");
        var longColumn = model.columns(List.of(new MeasuredShareAction(20,List.of(30f,30f,30f,30f,30f))),1,60);
        check(longColumn.getColumns().size()==1 && longColumn.getCapacity()>=170,"Long record extends one canvas instead of paging");
        var one = model.pages(List.of(new MeasuredShareAction(20,List.of(30f,30f))),80);
        check(one.size()==1 && one.get(0).get(0).getUntilRow()==2,"Exact-fit page");
        var continued = model.pages(List.of(new MeasuredShareAction(20,List.of(30f,30f,30f))),80);
        check(continued.size()==2 && continued.get(1).get(0).getFromRow()==2,"Overflow continues without omission");
        rejects(()->model.pages(List.of(),0),"Nonpositive page rejected");
        rejects(()->model.pages(List.of(new MeasuredShareAction(20,List.of(81f))),100),"Impossible row rejected");
        rejects(()->model.pages(List.of(new MeasuredShareAction(20,List.of(Float.NaN))),100),"NaN row rejected");
        // Variable heights, multiple identities, repeated headers: no duplicated/lost row and no overflow.
        Random random = new Random(817);
        for (int run=0; run<100;run++) {
            var measurements = new ArrayList<MeasuredShareAction>();
            int total=0;
            for (int a=0;a<1+random.nextInt(12);a++) {
                var rows = new ArrayList<Float>();
                for (int r=0;r<1+random.nextInt(30);r++) { rows.add(12f+random.nextInt(35)); total++; }
                measurements.add(new MeasuredShareAction(20+random.nextInt(40),rows));
            }
            var pages = model.pages(measurements,240);
            Set<String> seen = new HashSet<>();
            for (var page:pages) {
                float used=0;
                for (var slice:page) {
                    var m=measurements.get(slice.getAction()); used+=m.getHeaderHeight();
                    check(slice.getUntilRow()>slice.getFromRow(),"No orphaned headings");
                    for(int r=slice.getFromRow();r<slice.getUntilRow();r++) {
                        used+=m.getRowHeights().get(r);
                        check(seen.add(slice.getAction()+":"+r),"No row duplicated");
                    }
                }
                check(used<=240,"Page capacity respected");
            }
            check(seen.size()==total,"All measured rows exported");
            var single = model.columns(measurements,3,140);
            check(single.getColumns().size()<=3,"Single canvas respects the column count");
            Set<String> columnRows = new HashSet<>();
            for(var column:single.getColumns()) {
                float used=0;
                for(var slice:column) {
                    var m=measurements.get(slice.getAction()); used+=m.getHeaderHeight();
                    for(int r=slice.getFromRow();r<slice.getUntilRow();r++) {
                        used+=m.getRowHeights().get(r);
                        check(columnRows.add(slice.getAction()+":"+r),"Column flow does not duplicate a set");
                    }
                }
                check(used<=single.getCapacity()+.01,"Column fits its extended canvas");
            }
            check(columnRows.size()==total,"Single image keeps every measured set");
        }
        var gaussian=PhotoGaussianBlur.INSTANCE;
        int[] uniform=new int[35]; Arrays.fill(uniform,0xFF428791);
        int[] copy=uniform.clone();
        int[] result=gaussian.apply(uniform,7,5,4,()->Unit.INSTANCE);
        check(Arrays.equals(uniform,copy),"Blur must not mutate input");
        check(Arrays.equals(result,uniform),"Uniform photograph stays uniform including edges");
        int[] impulse=new int[81]; Arrays.fill(impulse,0xFF000000); impulse[40]=0xFFFFFFFF;
        int[] soft=gaussian.apply(impulse,9,9,3,()->Unit.INSTANCE);
        check((soft[40]&255)>0 && (soft[40]&255)<255,"Impulse genuinely softened");
        check((soft[39]&255)>0 && soft[39]==soft[41] && soft[31]==soft[49],"Kernel spreads symmetrically");
        check((soft[40]&255)>(soft[39]&255),"Kernel peaks at center");
        rejects(()->gaussian.apply(new int[2],2,2,2,()->Unit.INSTANCE),"Invalid pixel extent rejected");
        rejects(()->gaussian.apply(new int[1],1,1,0,()->Unit.INSTANCE),"Invalid radius rejected");
        try { gaussian.apply(uniform,7,5,4,()->{throw new java.util.concurrent.CancellationException();}); throw new AssertionError("Cancellation ignored"); }
        catch(java.util.concurrent.CancellationException expected) {checks++;}
        System.out.println(checks+" workout share pagination / Gaussian checks passed");
    }
}
