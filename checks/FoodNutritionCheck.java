import com.fitlog.app.domain.*;
import com.fitlog.app.data.FoodJson;
import java.util.*;

/** Fractional portions, missing nutrients, validation and persisted cart snapshots. */
public final class FoodNutritionCheck {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("check " + checks); checks++; }
    private static FoodSpec food(String id, Double kcal, Double protein) {
        return new FoodSpec(id, "鸡胸肉", "家常", "肉类", "150克", 150.0, kcal, protein, 0.0, 5.4,
            "file:///android_asset/foods/home-chicken-breast.webp", "估算", "", "AI生成", true);
    }
    public static void main(String[] args) {
        FoodNutrition math = FoodNutrition.INSTANCE;
        FoodSpec chicken = food("a", 247.5, 46.5);
        check(chicken.valid());
        for (double q : new double[] {.1, .5, 1, 2, 1000}) {
            List<FoodPortion> meal = List.of(new FoodPortion(chicken, q));
            check(math.canSave(meal));
            check(Math.abs(math.total(meal, "calories") - 247.5*q) < 0.000001);
            check(Math.abs(math.total(meal, "protein") - 46.5*q) < 0.000001);
            String encoded = FoodJson.INSTANCE.cart(meal);
            List<FoodPortion> restored = FoodJson.INSTANCE.cart(encoded);
            check(meal.equals(restored));
        }
        for (double q : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 1000.1}) {
            check(!math.canSave(List.of(new FoodPortion(chicken, q))));
        }
        List<FoodPortion> partial = List.of(new FoodPortion(chicken, 1), new FoodPortion(food("b", 100.0, null), 2));
        check(math.total(partial, "protein") == null);
        check(math.total(partial, "calories") == 447.5);
        check(math.canSave(partial));
        check(FoodJson.INSTANCE.cart(FoodJson.INSTANCE.cart(partial)).equals(partial));
        check(!math.canSave(List.of(new FoodPortion(food("c", null, null), 1))));
        check(math.total(List.of(new FoodPortion(food("c", null, null), 1)), "calories") == null);
        check(!food("d", Double.NaN, 2.0).valid());
        check(!food("d", -1.0, 2.0).valid());
        check(!math.canSave(Collections.emptyList()));
        check(math.total(Collections.emptyList(), "protein") == null);
        check(!math.canSave(Collections.nCopies(201, new FoodPortion(chicken, 1))));
        check(FoodJson.INSTANCE.cart("invalid").isEmpty());
        check(FoodJson.INSTANCE.cart("[{\"quantity\":1}]").isEmpty());
        check(FoodCatalogKt.foodNumber(123.456).equals("123.5"));
        check(FoodCatalogKt.foodNumber(1.25).equals("1.3"));
        check(FoodCatalogKt.foodNumber(100.0).equals("100"));
        check(FoodCatalogKt.foodNumber(0.04).equals("0"));
        check(FoodCatalogKt.foodNumber(-0.0).equals("0"));
        check(FoodCatalogKt.foodNumber(Double.NaN).equals("—"));
        FoodSpec mixed = new FoodSpec("ratio", "比例测试", "家常", "参考", "1份", null,
            999.0, 25.0, 25.0, 10.0, "", "", "", "", false);
        List<Double> shares = math.energyShares(List.of(new FoodPortion(mixed, 1)));
        check(Math.abs(shares.get(0) - 100.0/290) < 0.000001);
        check(Math.abs(shares.get(1) - 100.0/290) < 0.000001);
        check(Math.abs(shares.get(2) - 90.0/290) < 0.000001);
        check(Math.abs(shares.stream().mapToDouble(Double::doubleValue).sum()-1) < 0.000001);
        check(math.energyShares(partial) == null);
        check(math.energyShares(Collections.emptyList()) == null);
        FoodSpec zero = new FoodSpec("zero", "水", "家常", "参考", "1份", null,
            0.0, 0.0, 0.0, 0.0, "", "", "", "", false);
        check(math.energyShares(List.of(new FoodPortion(zero, 1))) == null);
        // Formatting must not mutate an existing intake or its stored precision.
        check(mixed.getCalories() == 999.0 && new FoodPortion(mixed, 0.125).getQuantity() == 0.125);
        FoodPortions portions = FoodPortions.INSTANCE;
        List<FoodPortion> selected = portions.adjust(Collections.emptyList(), chicken, .5);
        check(selected.size() == 1 && selected.get(0).getQuantity() == .5);
        check(portions.adjust(selected, chicken, -.5).isEmpty());
        check(portions.adjust(Collections.emptyList(), chicken, -.5).isEmpty());
        check(portions.adjust(List.of(new FoodPortion(chicken, .1)), chicken, .5).get(0).getQuantity() == .6);
        check(portions.adjust(List.of(new FoodPortion(chicken, 1000)), chicken, .5).get(0).getQuantity() == 1000);
        FoodSpec corrected = food("a", 100.0, null);
        List<FoodPortion> correctedMeal = portions.adjust(List.of(new FoodPortion(corrected, 1), new FoodPortion(mixed, 2)), chicken, .5);
        check(correctedMeal.get(0).getFood().equals(corrected) && correctedMeal.get(0).getQuantity() == 1.5);
        check(correctedMeal.get(1).getQuantity() == 2);
        check(portions.adjust(selected, chicken, Double.NaN).equals(selected));
        check(portions.adjust(Collections.nCopies(200, new FoodPortion(mixed, 1)), chicken, .5).size() == 200);
        System.out.println("Food portions, unknowns and persisted drafts: " + checks + " checks passed");
    }
}
