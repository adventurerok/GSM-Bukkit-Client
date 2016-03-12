package com.ithinkrok.msm.client.protocol;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 11/03/16.
 */
public class ClientUpdateFileProtocol extends ClientUpdateBaseProtocol {

    protected final Path basePath;

    public ClientUpdateFileProtocol(boolean primary, Path basePath) {
        super(primary);
        this.basePath = basePath;
    }

    @Override
    protected Map<String, Instant> getResourceVersions() {
        Map<String, Instant> result = new HashMap<>();

        Set<FileVisitOption> options = new HashSet<>();
        options.add(FileVisitOption.FOLLOW_LINKS);

        try {
            Files.walkFileTree(basePath, options, Integer.MAX_VALUE, new ResourceVisitor(result));
        } catch (IOException e) {
            System.out.println("Failed to walk file tree for path: " + basePath);
            e.printStackTrace();
        }

        return result;
    }

    protected String getResourceName(Path path) {
        return basePath.relativize(path).toString();
    }

    @Override
    protected boolean updateResource(String name, byte[] update) {
        Path resourcePath = getResourcePath(name);
        if(resourcePath == null) return false;

        Path resourceDirectory = resourcePath.getParent();

        if (!ensureDirectoryExists(resourceDirectory)) return false;
        if (!writeResourceUpdate(resourcePath, update)) return false;

        System.out.println("Updated resource at" + resourcePath);

        return true;
    }

    private boolean writeResourceUpdate(Path resourcePath, byte[] update) {
        try {
            Files.write(resourcePath, update);
        } catch (IOException e) {
            System.out.println("Error while saving updated resource " + resourcePath);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean ensureDirectoryExists(Path resourceDirectory) {
        if(!Files.exists(resourceDirectory)) {
            try{
                Files.createDirectories(resourceDirectory);
            } catch (IOException e) {
                System.out.println("Failed to create directory for resource update : " + resourceDirectory);
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    protected Path getResourcePath(String name) {
        return basePath.resolve(name);
    }

    private class ResourceVisitor extends SimpleFileVisitor<Path> {

        private final Map<String, Instant> result;

        public ResourceVisitor(Map<String, Instant> result) {
            this.result = result;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String resourceName = getResourceName(file);
            if(resourceName != null) {
                Instant dateModified = Files.getLastModifiedTime(file).toInstant();

                result.put(resourceName, dateModified);
            }

            return FileVisitResult.CONTINUE;
        }
    }
}
