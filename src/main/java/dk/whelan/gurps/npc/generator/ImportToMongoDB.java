package dk.whelan.gurps.npc.generator;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.lang.System.out;
import static java.lang.System.setOut;

/**
 * Created by lnorregaard on 04/12/15.
 */
public class ImportToMongoDB {

    public static MongoClient mongoClient = null;
    public static MongoDatabase db = null;


    public static void main(String[] args) throws IOException {
        String pathString = "Library";
        if (args.length > 0) {
            pathString = args[0];
        }
        if (args.length == 2) {
            mongoClient = new MongoClient(new MongoClientURI(args[1]));
        } else {
            mongoClient = new MongoClient();
        }
        mongoClient.dropDatabase("gurps");
        db = mongoClient.getDatabase("gurps");

        Path path = FileSystems.getDefault().getPath(pathString);

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(path, "{Advantages,Equipment,Skills,Spells}")) {
            stream.forEach(ImportToMongoDB::insertFiles);
        }
        resetAllskillsTL();
        setTLForSpecificSkills(FileSystems.getDefault().getPath("Library/tlSkills.txt"));


    }

    private static void setTLForSpecificSkills(Path path) {
        try (Stream<String> stream = Files.lines(path)) {
            stream.forEach(ImportToMongoDB::updateSkillTL);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void updateSkillTL(String skillAndTL) {
        String[] splitStrings = skillAndTL.split(";");
        UpdateResult result = db.getCollection("skill").updateMany(new Document("name", splitStrings[0]).append("specialization", splitStrings[1]),
                new Document("$set", new Document("tl", Integer.valueOf(splitStrings[2]))));

    }

    private static void resetAllskillsTL() {
        UpdateResult result = db.getCollection("skill").updateMany(new Document(),
                new Document("$set", new Document("tl", 10)));
    }

    private static void insertFiles(Path path) {
        try {
            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(path, "*")) {
                stream.forEach(ImportToMongoDB::skillParse);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void skillParse(Path path) {
        String filename = path.getFileName().toString();
        String book = filename.substring(0,filename.lastIndexOf("."));
        try {
            String content = new String(Files.readAllBytes(path));
            JSONObject xmlJSONObj = XML.toJSONObject(content);
            xmlJSONObj = findLowestArray(xmlJSONObj);
            for (String key : xmlJSONObj.keySet()) {
                Object obj = xmlJSONObj.get(key);
                if (obj instanceof JSONArray) {
                    saveToDb((JSONArray)obj,key,book);
                }
            }
        } catch (JSONException je) {
            out.println(je.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static JSONObject findLowestArray(JSONObject jsonObject) {
        JSONArray array = null;
        for (String s : jsonObject.keySet()) {
            Object obj = jsonObject.get(s);
            if (obj instanceof JSONArray) {
                array = (JSONArray) obj;
                break;
            }
        }
        if (array != null) {
            return jsonObject;
        } else {
            return findLowestArray(jsonObject.getJSONObject(jsonObject.keys().next()));
        }
    }

    private static void saveToDb(JSONArray array, String collection, String book) {
        if (collection.contains("_")) {
            collection = collection.substring(0,collection.lastIndexOf("_"));
        }
        final String finalCollection = collection;
        array.forEach(e -> db.getCollection(finalCollection).insertOne(Document.parse(e.toString()).append("book",book)));
    }
}
