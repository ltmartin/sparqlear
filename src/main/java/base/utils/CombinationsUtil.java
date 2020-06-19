package base.utils;


import java.util.ArrayList;
import java.util.List;

public class CombinationsUtil {
    public static List<List<String>> generateCombinations(int n) {
        List<List<String>> combinations = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<String> newRow = new ArrayList<>();
            newRow.add(String.valueOf(i));
            combinations.add(newRow);
            for (int j = 0; j < i; j++) {
                List<String> row = combinations.get(j);
                int rowSize = row.size();
                for (int k = 0; k < rowSize; k++) {
                    String valueString = row.get(k);
                    valueString += " " + String.valueOf(i);
                    row.add(valueString);
                }
                combinations.remove(j);
                combinations.add(j, row);
            }
        }
        return combinations;
    }
}
