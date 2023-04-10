package io.mats3.examples;

import io.mats3.serial.json.MatsSerializerJson;
import org.junit.Assert;
import org.junit.Test;

/**
 * Since this is a Java 17 project, I choose to test the serialization mechanism for Records here.
 *
 * @author Endre Stølsvik 2023-03-28 22:09 - http://stolsvik.com/, endre@stolsvik.com
 */
public class TestRecordsSerialization {

    record RecordTest(String string, int integer) {
    }

    @Test
    public void straight_back_and_forth() {
        MatsSerializerJson serializer = MatsSerializerJson.create();

        RecordTest recordTest = new RecordTest("endre", 1);

        System.out.println("RecordTest:              " + recordTest);
        String json = serializer.serializeObject(recordTest);
        System.out.println("JSON serialized:         " + json);
        Assert.assertEquals("{\"string\":\"endre\",\"integer\":1}", json);

        RecordTest recordTest_deserialized = serializer.deserializeObject(json, RecordTest.class);
        System.out.println("RecordTest deserialized: " + recordTest_deserialized);
        Assert.assertEquals(recordTest, recordTest_deserialized);
    }

    @Test
    public void missingJsonField() {
        MatsSerializerJson serializer = MatsSerializerJson.create();

        // Missing the string
        String json = "{\"integer\":1}";

        RecordTest recordTest_deserialized = serializer.deserializeObject(json, RecordTest.class);
        System.out.println("RecordTest deserialized: " + recordTest_deserialized);
        Assert.assertEquals(new RecordTest(null, 1), recordTest_deserialized);

        // Missing the integer
        json = "{\"string\":\"endre\"}";

        recordTest_deserialized = serializer.deserializeObject(json, RecordTest.class);
        System.out.println("RecordTest deserialized: " + recordTest_deserialized);
        Assert.assertEquals(new RecordTest("endre", 0), recordTest_deserialized);
    }

    @Test
    public void extraJsonField() {
        MatsSerializerJson serializer = MatsSerializerJson.create();

        // Extra fields
        String json = "{\"string\":\"endre\", \"integer\":1, \"string2\":\"kamel\", \"integer2\":1, \"bool\":true}";

        RecordTest recordTest_deserialized = serializer.deserializeObject(json, RecordTest.class);
        System.out.println("RecordTest deserialized: " + recordTest_deserialized);
        Assert.assertEquals(new RecordTest("endre", 1), recordTest_deserialized);

        // Missing the string, but also extra fields
        json = "{\"integer\":1, \"string2\":\"kamel\", \"integer2\":1, \"bool\":true}";

        recordTest_deserialized = serializer.deserializeObject(json, RecordTest.class);
        System.out.println("RecordTest deserialized: " + recordTest_deserialized);
        Assert.assertEquals(new RecordTest(null, 1), recordTest_deserialized);
    }

    @Test
    public void emptyJsonToRecord() {
        MatsSerializerJson serializer = MatsSerializerJson.create();

        // Empty JSON
        String json = "{}";

        RecordTest recordTest_deserialized = serializer.deserializeObject(json, RecordTest.class);
        System.out.println("RecordTest deserialized: " + recordTest_deserialized);
        Assert.assertEquals(new RecordTest(null, 0), recordTest_deserialized);
    }

    @Test
    public void newInstanceRecord() {
        MatsSerializerJson serializer = MatsSerializerJson.create();

        RecordTest recordTest_deserialized = serializer.newInstance(RecordTest.class);
        Assert.assertEquals(new RecordTest(null, 0), recordTest_deserialized);
    }
}
