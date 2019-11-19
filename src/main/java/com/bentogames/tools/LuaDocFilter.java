package com.bentogames.tools;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;


public class LuaDocFilter {

    private static final String TABLE_ROW = "tr";

    private static final String[] ACCEPTABLE_DATA_FILES = new String[] {
        "index", "Libarary.GlobalDataObject", "Library.GameObject", "Armies.Army", "Data.Battle", "Cities.City",
        "Data.GameTeam", "Structures.Structure", "Territories.Territory", "Generals.General", "ClientRequestSender"
    };

    private static final String[] ACCEPTABLE_CLIENT_REQUEST_SENDER_METHODS = new String[] {
        "getInstance", "sendArmy", "buyStructure", "sellStructure"
    };

    public static void main(String[] args) {
        String dirFromName = args[0];
        String dirToName = args[1];

        File dirFrom = new File(dirFromName);
        File dirTo = new File(dirToName);
        if (dirTo.exists()) {
            if (!deleteDirectory(dirTo)) {
                System.out.println("Cannot delete target directory, exiting.");
                System.exit(-1);
            }
        }
        dirTo.mkdirs();

        try {
            List<File> fileList = listFilesAndDirectoriesForFolder(dirFrom);
            for (File file : fileList) {
                // Only copy over html files

                if (file.isDirectory()) {
                    // Create directories
                    new File(getNewPathFromOldPath(dirFrom, dirTo, file).toString()).mkdirs();
                    continue;
                } else if (!file.getName().endsWith("html")) {
                    // Copy non-html files
                    Files.copy(file.toPath(), getNewPathFromOldPath(dirFrom, dirTo, file));
                    continue;
                } else if (Arrays.stream(ACCEPTABLE_DATA_FILES).noneMatch(file.getName()::contains)) {
                    // Skip files that do not pass the filter
                    continue;
                } else if (file.getAbsolutePath().contains("SharedSource.Data")) {
                    addDataFile(file, new File(getNewPathFromOldPath(dirFrom, dirTo, file).toString()));
                } else if (file.getAbsolutePath().contains("LocalSource.Publishers")) {
                    addClientRequestSender(file, new File(getNewPathFromOldPath(dirFrom, dirTo, file).toString()));
                }
            }
        } catch(FileNotFoundException e){
            // No-op
        } catch(IOException e){
            e.printStackTrace();
        }
    }

    private static void addDataFile(File fileFrom, File fileTo) throws IOException {
        String content = new Scanner(fileFrom).useDelimiter("\\Z").next();
        Document document = Jsoup.parse(content);

        Elements headers = document.getElementsByTag("h2");
        for (Element header : headers) {
            if (!header.text().equals("Modules")) {
                continue;
            }
            Element moduleList = header.nextElementSibling();
            for (Element moduleListItem : moduleList.children()) {
                Elements aTags = moduleListItem.getElementsByTag("a");
                if (!aTags.isEmpty()) {
                    String link = aTags.get(0).attr("href");
                    if (Arrays.stream(ACCEPTABLE_DATA_FILES).noneMatch(link::contains)) {
                        moduleListItem.remove();
                    }
                }
            }
        }

        // There should only be one tag with class 'function_list'
        Elements functionLists = document.getElementsByClass("function_list");
        for (Element functionList : functionLists) {
            for (Element row : functionList.getElementsByTag(TABLE_ROW)) {
                // <tr><td ...><a ...></a>FUNCTION_NAME</td> ...
                String functionName = row.child(0).child(0).text();
                if (!functionName.startsWith("get") && !functionName.startsWith("is")) {
                    row.remove();
                }
            }
        }
        // There should only be one tag with class 'function'
        Elements functions = document.getElementsByClass("function");
        for (Element function : functions) {
            // Search through each <dt></dt> and <dd></dd> pair
            for (Element element : function.getElementsByTag("dt")) {
                String functionName = element.getElementsByTag("a").get(0).attr("name");
                if (!functionName.startsWith("get") && !functionName.startsWith("is")) {
                    // Remove <dt></dt> and <dd></dd> tags
                    Element nextElement = element.nextElementSibling();
                    element.remove();
                    nextElement.remove();
                }
            }
        }

        // Write the file to new dir
        try (FileWriter writer = new FileWriter(fileTo)) {
            writer.write(document.toString());
        }
    }

    private static void addClientRequestSender(File fileFrom, File fileTo) throws IOException {
        String content = new Scanner(fileFrom).useDelimiter("\\Z").next();
        Document document = Jsoup.parse(content);

        // TODO: remove sensitive data

        // Write the file to new dir
        try (FileWriter writer = new FileWriter(fileTo)) {
            writer.write(document.toString());
        }
    }

    private static boolean deleteDirectory(final File folder) {
        for (final File fileEntry : Objects.requireNonNull(folder.listFiles())) {
            if (fileEntry.isDirectory()) {
                if (!deleteDirectory(fileEntry)) {
                    return false;
                }
            }
            if (!fileEntry.delete()) {
                return false;
            }
        }
        return true;
    }

    private static List<File> listFilesAndDirectoriesForFolder(final File folder) {
        List<File> fileList = new ArrayList<>();
        for (final File fileEntry : Objects.requireNonNull(folder.listFiles())) {
            fileList.add(fileEntry);

            if (fileEntry.isDirectory()) {
                fileList.addAll(listFilesAndDirectoriesForFolder(fileEntry));
            }
        }
        return fileList;
    }

    private static Path getNewPathFromOldPath(File dirFrom, File dirTo, File file) {
        String oldPath = file.getAbsolutePath();
        String base = dirFrom.getAbsolutePath();
        String relative = new File(base).toURI().relativize(new File(oldPath).toURI()).getPath();
        return Paths.get(dirTo.getAbsolutePath(), relative);
    }
}