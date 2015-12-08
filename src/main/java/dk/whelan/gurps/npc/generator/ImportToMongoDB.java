package dk.whelan.gurps.npc.generator;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

import java.io.IOException;
import java.nio.file.*;

import static java.lang.System.out;

/**
 * Created by lnorregaard on 04/12/15.
 */
public class ImportToMongoDB {

    public static MongoClient mongoClient = null;
    public static MongoDatabase db = null;


    public static void main(String[] args) throws IOException {
        String pathString = "Library";
        if (args.length == 2) {
            pathString = args[0];
            mongoClient = new MongoClient(new MongoClientURI(args[1]));
        } else {
            mongoClient = new MongoClient();
        }

        db = mongoClient.getDatabase("gurps");
        System.out.println(FileSystems.getDefault().getRootDirectories().iterator().next());
        Path path = FileSystems.getDefault().getPath(pathString);

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(path, "{Advantages,Equipment,Skills,Spells}")) {
            stream.forEach(ImportToMongoDB::insertFiles);
        }
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
        System.out.println(path);
        String filename = path.getFileName().toString();
        String book = filename.substring(0,filename.lastIndexOf("."));
        try {
            String content = new String(Files.readAllBytes(path));
            JSONObject xmlJSONObj = XML.toJSONObject(content);
            xmlJSONObj = findLowestArray(xmlJSONObj);
            System.out.println(xmlJSONObj.keySet());
            if (path.toString().equals("Library/Skills/Dragons.skl")) {
                String t = "";
            }
//            System.out.println(xmlJSONObj);
            final JSONObject finalXmlJSONObj = xmlJSONObj;
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
//        if (object == null) {
//          return null;
//        } else if (object instanceof JSONArray) {
//            JSONArray xmlJSONArray = (JSONArray) object;
//            return xmlJSONArray;
//        } else if (object instanceof JSONObject){
//            JSONObject xmlJSONObj = (JSONObject) object;
//            Optional<String> key = xmlJSONObj.keySet()
//                    .stream()
//                    .filter(e -> e.endsWith("list") || e.endsWith("container"))
//                    .findFirst();
//            if (key.isPresent()) {
//                return findLowestArray(xmlJSONObj.get(key.get()));
//            } else {
//                return null;
//            }
//        }
    }

    private static void saveToDb(JSONArray array, String collection, String book) {
        if (collection.contains("_")) {
            collection = collection.substring(0,collection.lastIndexOf("_"));
        }
        final String finalCollection = collection;
        array.forEach(e -> db.getCollection(finalCollection).insertOne(Document.parse(e.toString()).append("book",book)));
    }
}