package utils.DbPedia;

import config.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@SpringBootTest(classes = Application.class)
class DbpediaLogFileProcessorTest {
    @Autowired
    private DbpediaLogFileProcessor dbpediaLogFileProcessor;

    @Test
    void processLogFile() {
        try (Stream<Path> paths = Files.walk(Paths.get("queryLogs"))){
            paths
                    .filter(Files::isRegularFile)
                    .forEach((e) -> {
                        dbpediaLogFileProcessor.processLogFile(e.toFile());
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}