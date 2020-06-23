package base.utils;


import java.util.ArrayList;
import java.util.List;

public class CombinationsUtil {
    public static List<List<String>> generateCombinations(int n) {
        int count = 0;
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
                    valueString += " " + i;
                    row.add(valueString);
                    count++;
                }
                combinations.remove(j);
                combinations.add(j, row);
                if (count > 1000000)
                    return combinations;
            }
        }
        return combinations;
    }
}
