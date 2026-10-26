import com.fitlog.app.domain.FoodTags;
import java.util.*;

public class FoodTagsCheck {
    static int count;
    static void check(boolean ok) { count++; if (!ok) throw new AssertionError("Food tag check " + count); }
    public static void main(String[] args) {
        FoodTags tags = FoodTags.INSTANCE;
        check(tags.normalize("  我的  早餐  ").equals("我的 早餐"));
        check(!tags.valid("  "));
        check(!tags.valid(" 全部 "));
        check(!tags.valid("x".repeat(21)));
        check(tags.valid("x".repeat(20)));
        check(tags.options(Collections.emptyList(), " 自定义 ").equals(Arrays.asList("自定义")));
        check(tags.options(Collections.emptyList(), "早餐").equals(Arrays.asList("早餐", "自定义")));
        check(tags.options(Collections.emptyList(), "全部").equals(Arrays.asList("自定义")));
        System.out.println("Food tags: " + count + " checks passed");
    }
}
