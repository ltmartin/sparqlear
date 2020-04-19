package utils.DbPedia;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import utils.UtilsJena;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@Lazy
public class DbpediaLogFileProcessor {
    private final Logger logger = Logger.getLogger(UtilsJena.class.getName());

    public void processLogFile(File file){
        LinkedList<String> queries = new LinkedList<>();
        String line = null;
        try (BufferedReader br = new BufferedReader(new FileReader(file.toString()))){
            while ((line = br.readLine()) != null) {
                int queryStartingPostion = line.indexOf("SELECT");
                int queryEndingPosition = line.indexOf("&format=");
                queryEndingPosition = (queryEndingPosition < 0)? line.length() : queryEndingPosition;
                queryEndingPosition = (queryEndingPosition < queryStartingPostion)? line.length() : queryEndingPosition;

                if (queryStartingPostion >= 0) {
                    line = line.substring(queryStartingPostion, queryEndingPosition);
                    try {
                        line = URLDecoder.decode(line, StandardCharsets.UTF_8.toString());
                        line = line.replaceAll("\n", " ").replaceAll("\r", " ");
                        queries.add(line);
                    } catch (IllegalArgumentException e){
                        System.out.println("===============================================");
                        logger.log(Level.SEVERE, "Error processing the line: \n" + line + "\n");
                        System.out.println("===============================================");
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e){
            System.out.println("===============================================");
            logger.log(Level.SEVERE, "Error processing the line: \n" + line + "\n");
            e.printStackTrace();
            System.out.println("===============================================");
        }

        try {
            FileWriter writer = new FileWriter(file.toString() + "-processed");
            queries.forEach((e) -> {
                try {
                    writer.write(e.toString() + "\n");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
