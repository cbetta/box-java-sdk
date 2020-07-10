package com.box.sdk;


import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

/**
 * {@link BoxZip} related integration and unit tests.
 */
public class BoxZipTest {
    @ClassRule
    public static final WireMockClassRule WIRE_MOCK_CLASS_RULE = new WireMockClassRule(53621);
    private BoxAPIConnection api = TestConfig.getAPIConnection();

    @Test
    @Category(IntegrationTest.class)
    public void createAndDownloadZipSucceeds() throws IOException {
        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getAccessToken());
        BoxFolder rootFolder = BoxFolder.getRootFolder(api);

        String fileName = "small_file.rtf";
        URL fileURL = this.getClass().getResource("/sample-files/" + fileName);
        String filePath = URLDecoder.decode(fileURL.getFile(), "utf-8");
        long fileSize = new File(filePath).length();
        byte[] fileContent = readAllBytes(filePath);

        String zipFileName = "zip_test.zip";
        URL zipFileURL = this.getClass().getResource("/sample-files/" + zipFileName);
        String zipFilePath = URLDecoder.decode(zipFileURL.getFile(), "utf-8");
        byte[] zipFileContent = readAllBytes(zipFilePath);

        InputStream uploadStream = new FileInputStream(filePath);
        BoxFile.Info uploadedFileInfo = rootFolder.uploadFile(uploadStream,
            "Zip Test File 1.rtf");
        BoxFile uploadedFile = uploadedFileInfo.getResource();
        BoxFolder.Info createdFolderInfo = rootFolder.createFolder("Zip Test Folder");
        BoxFolder createdFolder = createdFolderInfo.getResource();
        uploadedFile.copy(createdFolder, "Zip Test File 2.rtf");

        ArrayList<JsonObject> items = new ArrayList<JsonObject>();
        JsonObject file = new JsonObject().add("id", uploadedFileInfo.getID()).add("type", "file");
        JsonObject folder = new JsonObject().add("id", createdFolderInfo.getID()).add("type",
            "folder");
        items.add(file);
        items.add(folder);

        ByteArrayOutputStream downloadStream = new ByteArrayOutputStream();
        BoxZipDownloadStatus zipDownloadStatus = new BoxZip(api).download("zip_test", items, downloadStream);
        byte[] downloadedFileContent = downloadStream.toByteArray();

        createdFolder.delete(true);
        uploadedFile.delete();

        // File bytes for zips will not always be equal since they are being generated by every test.
        // To approximate that the files are equal, the assertion below checks the lengths.
        Assert.assertEquals(zipFileContent.length, downloadedFileContent.length);
        Assert.assertEquals(BoxZipDownloadStatus.State.SUCCEEDED, zipDownloadStatus.getState());
    }

    @Test
    @Category(UnitTest.class)
    public void createZipSucceeds() throws IOException, ParseException {
        String result = "";
        final String fileID = "466239504569";
        final String folderID = "466239504580";
        final String downloadFileName = "test";

        final String downloadURL = "https://api.box.com/zip_downloads/124hfiowk3fa8kmrwh/content";
        final String statusURL = "https://api.box.com/zip_downloads/124hfiowk3fa8kmrwh/status";
        final Date expiresAt = BoxDateFormat.parse("2018-04-25T11:00:18-07:00");

        ArrayList<JsonObject> items = new ArrayList<JsonObject>();
        JsonArray itemsBody = new JsonArray();
        JsonObject file = new JsonObject().add("id", fileID).add("type", "file");
        JsonObject folder = new JsonObject().add("id", folderID).add("type",
            "folder");
        items.add(file);
        items.add(folder);
        itemsBody.add(file).add(folder);

        JsonObject body = new JsonObject()
            .add("items", itemsBody)
            .add("download_file_name", downloadFileName);

        result = TestConfig.getFixture("BoxZip/CreateZipFile202");

        WIRE_MOCK_CLASS_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/zip_downloads"))
            .withRequestBody(WireMock.equalToJson(body.toString()))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result)));

        BoxZip zip = new BoxZip(this.api);
        BoxZipInfo zipInfo = zip.create(downloadFileName, items);

        Assert.assertEquals(downloadURL, zipInfo.getDownloadURL().toString());
        Assert.assertEquals(statusURL, zipInfo.getStatusURL().toString());
        Assert.assertEquals(expiresAt, zipInfo.getExpiresAt());
        Assert.assertEquals("100", zipInfo.getNameConflicts().get(0).get(0).getID());
        Assert.assertEquals("salary.pdf", zipInfo.getNameConflicts().get(0).get(0).getOriginalName());
        Assert.assertEquals("aqc823.pdf", zipInfo.getNameConflicts().get(0).get(0).getDownloadName());
        Assert.assertEquals("200", zipInfo.getNameConflicts().get(0).get(1).getID());
        Assert.assertEquals("salary.pdf", zipInfo.getNameConflicts().get(0).get(1).getOriginalName());
        Assert.assertEquals("aci23s.pdf", zipInfo.getNameConflicts().get(0).get(1).getDownloadName());
    }

    protected static byte[] readAllBytes(String fileName) throws IOException {
        RandomAccessFile f = new RandomAccessFile(fileName, "r");
        byte[] b = new byte[(int) f.length()];
        f.read(b);
        return b;
    }
}

