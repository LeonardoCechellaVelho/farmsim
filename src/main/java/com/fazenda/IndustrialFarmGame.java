package com.fazenda;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class IndustrialFarmGame extends Application {

    private static final int TILE_SIZE = 12;
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final int MAP_SIZE = 200;

    private static final int TERRAIN_GRASS = 0;
    private static final int TERRAIN_DIRT = 1;
    private static final int TERRAIN_PLANTED = 2;
    private static final int TERRAIN_GRAVEL_ROAD = 3;
    private static final int TERRAIN_LIGHT_DIRT = 4;

    private static final int TOOL_PLOW = 0;
    private static final int TOOL_PLANTER = 1;
    private int currentToolType = TOOL_PLOW;

    private final int SHED_X = 60;
    private final int SHED_Y = 60;

    private final int SHED_W = 6;
    private final int SHED_H = 8;

    private boolean[][] treeMap = new boolean[MAP_SIZE][MAP_SIZE];

    private Image imgPlowed, imgRoad, imgRoadBorder;
    private Image[] grassVariants = new Image[64];
    private Image[] darkGrassVariants = new Image[64];
    private Image[] plowedPebbleVariants = new Image[64];
    private Image[] seedlingVariants = new Image[64];
    private Image[] gravelVariants = new Image[16];

    private Image treeSheet;
    private Image shedImage;

    private final double TREE_W = 416.0 / 4.0;
    private final double TREE_H = 541.0 / 2.0;

    private int[][] noiseMap = new int[MAP_SIZE][MAP_SIZE];
    private javafx.scene.image.WritableImage miniMapImage = new javafx.scene.image.WritableImage(MAP_SIZE, MAP_SIZE);
    private int lastPlowCol = -1, lastPlowRow = -1;
    private Image tractorSheet;
    private final double SPRITE_W = 931.0 / 6.0;
    private final double SPRITE_H = 472.0 / 4.0;
    private final double TRACTOR_SCALE = 0.4;

    private double tractorX = 500, tractorY = 500;
    private double angle = 45, smoothedAngle = 45;
    private double currentSpeed = 0;

    private final double MAX_SPEED_KMH = 30.0;
    private final double MAX_SPEED = 2.6;
    private final double ACCELERATION = 0.008;
    private final double FRICTION = 0.015;
    private final double BRAKE_FORCE = 0.04;
    private final double BASE_ROTATION = 1.2;

    private double trailerX = 430, trailerY = 430;
    private double trailerAngle = 45;
    private final double TRAILER_DISTANCE = 60.0;
    private boolean isAttached = false;

    private double cameraX, cameraY;
    private Set<KeyCode> activeKeys = new HashSet<>();
    private int[][] farmMap = new int[MAP_SIZE][MAP_SIZE];

    private double miniMapVR = 25.0;

    private long lastFrameTime = 0;

    @Override
    public void start(Stage stage) {
        createTileCache();

        for (int r = 0; r < MAP_SIZE; r++) {
            for (int c = 0; c < MAP_SIZE; c++) {
                noiseMap[r][c] = (int) (getNoise(r, c) * 63);
                miniMapImage.getPixelWriter().setColor(c, r, Color.TRANSPARENT);
            }
        }

        for (int r = 0; r < MAP_SIZE; r++) {
            for (int c = 0; c < MAP_SIZE; c++) {

                if (c < 12) {
                    treeMap[r][c] = false;
                    continue;
                }

                if (r >= 0 && r < MAP_SIZE && c >= 12 && c < MAP_SIZE) {
                    double nVal = noiseMap[r][c] / 63.0;

                    treeMap[r][c] = (nVal > 0.60);
                } else {
                    treeMap[r][c] = false;
                }
            }
        }

        int startCol = 12;
        int endCol = SHED_X + SHED_W / 2;
        int pathCenterRow = SHED_Y + SHED_H + 3;

        for (int c = startCol; c <= endCol + 4; c++) {
            int rowOffset = (int) (Math.sin((c - startCol) * 0.15) * 5);
            int rBase = pathCenterRow + rowOffset;

            for (int w = -6; w <= 6; w++) {
                int r = rBase + w;
                if (r >= 0 && r < MAP_SIZE && c >= 0 && c < MAP_SIZE) {
                    farmMap[r][c] = TERRAIN_GRAVEL_ROAD;
                    miniMapImage.getPixelWriter().setColor(c, r, Color.web("#6A5D4D"));
                }
            }
        }

        int patioCenterX = SHED_X + SHED_W / 2;
        int patioCenterY = SHED_Y + SHED_H / 2;
        int baseRadius = 18;

        for (int j = patioCenterY - baseRadius - 5; j < patioCenterY + baseRadius + 10; j++) {
            for (int i = patioCenterX - baseRadius - 10; i < patioCenterX + baseRadius + 10; i++) {
                if (i >= 0 && i < MAP_SIZE && j >= 0 && j < MAP_SIZE) {

                    double dx = i - patioCenterX;
                    double dy = j - patioCenterY;
                    double dist = Math.sqrt(dx * dx + dy * dy);

                    double angle = Math.atan2(dy, dx);
                    double deformation = Math.sin(angle * 5) * 2.5 + Math.cos(angle * 3) * 2.0;

                    if (dist < baseRadius + deformation) {
                        farmMap[j][i] = TERRAIN_GRAVEL_ROAD;
                        miniMapImage.getPixelWriter().setColor(i, j, Color.web("#6A5D4D"));
                    }
                }
            }
        }

        try {
            tractorSheet = new Image(getClass().getResourceAsStream("/trator.png"));
            treeSheet = new Image(getClass().getResourceAsStream("/trees.png"));
            shedImage = new Image(getClass().getResourceAsStream("/shed.png"));
        } catch (Exception e) {
            System.err.println("Erro: Imagens não encontradas. Verifique trator.png, trees.png e shed.png.");
        }

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        scene.setOnKeyPressed(e -> {
            activeKeys.add(e.getCode());
            if (e.getCode() == KeyCode.C)
                toggleCouping();
            if (e.getCode() == KeyCode.Q)
                toggleTool();
        });

        scene.setOnKeyReleased(e -> activeKeys.remove(e.getCode()));

        scene.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double mx = e.getX(), my = e.getY();
                double sz = 140, cx = (WIDTH - sz - 20) + sz / 2, cy = 20 + sz / 2;
                if (Math.hypot(mx - (cx + 45), my - (cy + 45)) < 12)
                    miniMapVR = Math.max(8, miniMapVR - 4);
                if (Math.hypot(mx - (cx + 15), my - (cy + 60)) < 12)
                    miniMapVR = Math.min(90, miniMapVR + 4);
            }
        });

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameTime == 0)
                    lastFrameTime = now;
                double delta = (now - lastFrameTime) / 1_000_000_000.0;
                lastFrameTime = now;

                update(delta);
                render(gc);
            }
        }.start();

        int transitionRadius = 2;

        for (int r = 0; r < MAP_SIZE; r++) {
            for (int c = 0; c < MAP_SIZE; c++) {

                // Só altera terrenos naturais
                if (farmMap[r][c] == TERRAIN_GRASS || farmMap[r][c] == TERRAIN_DIRT) {

                    int d = distanceToTerrain(r, c, TERRAIN_GRAVEL_ROAD, transitionRadius);

                    // Primeira e segunda camada SEMPRE light dirt
                    if (d == 1) {
                        farmMap[r][c] = TERRAIN_LIGHT_DIRT;
                    }
                }
            }
        }

        stage.setTitle("Farm Simulator - Arado e Plantadeira");
        stage.setScene(scene);
        stage.show();
    }

    private void drawZoomButton(GraphicsContext gc, int size, double x, double y, String text) {
        double r = 14;
        gc.setFill(Color.web("#111111"));
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.2);
        gc.fillOval(x - r, y - r, r * 2, r * 2);
        gc.strokeOval(x - r, y - r, r * 2, r * 2);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, size));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(text, x, y + 5);
    }

    private void toggleTool() {
        currentToolType = (currentToolType == TOOL_PLOW) ? TOOL_PLANTER : TOOL_PLOW;
    }

    private void createTileCache() {
        imgRoadBorder = createSingleTileImage(Color.web("#808080"), 0, false, false, false, null);
        imgRoad = createSingleTileImage(Color.web("#2c2c2c"), 0, false, false, false, null);
        imgPlowed = createSingleTileImage(Color.web("#3d2611"), 0, false, true, false, null);

        Color grassColor = Color.web("#2d4c21");
        Color bladeColor = Color.web("#3a5f27");
        Color darkGrassColor = Color.web("#1a2b13");
        Color darkBladeColor = Color.web("#233a1a");
        Color plowedColor = Color.web("#3d2611");

        for (int i = 0; i < 64; i++) {
            grassVariants[i] = createSingleTileImage(grassColor, i, true, false, false, bladeColor);
            darkGrassVariants[i] = createSingleTileImage(darkGrassColor, i, true, false, false, darkBladeColor);
            plowedPebbleVariants[i] = createSingleTileImage(plowedColor, i, false, true, false, null);
            seedlingVariants[i] = createSingleTileImage(plowedColor, i, false, false, true, null);
        }
        for (int i = 0; i < 16; i++) {
            gravelVariants[i] = createHeavyGravelTile(Color.web("#453d33"), i);
        }
    }

    private Image createHeavyGravelTile(Color baseColor, int seed) {
        int w = TILE_SIZE * 2;
        int h = TILE_SIZE + 15;
        Canvas temp = new Canvas(w, h);
        GraphicsContext tgc = temp.getGraphicsContext2D();
        double off = 10;
        double[] xs = { TILE_SIZE, TILE_SIZE * 2, TILE_SIZE, 0 };
        double[] ys = { off, TILE_SIZE / 2.0 + off, TILE_SIZE + off, TILE_SIZE / 2.0 + off };

        tgc.setFill(baseColor.darker());
        tgc.fillPolygon(xs, ys, 4);

        Random rng = new Random(seed * 999);

        for (int i = 0; i < 250; i++) {
            double px = rng.nextDouble() * (TILE_SIZE * 2);
            double py = off + rng.nextDouble() * TILE_SIZE;
            tgc.setFill(baseColor.deriveColor(rng.nextDouble() * 20 - 10, 1, 0.8, 0.3));
            tgc.fillOval(px, py, 1.5, 1.5);
        }

        int pebbleCount = 40 + rng.nextInt(20);
        for (int i = 0; i < pebbleCount; i++) {
            double px = rng.nextDouble() * (TILE_SIZE * 1.8);
            double py = off + rng.nextDouble() * (TILE_SIZE * 0.9);
            double size = 2.0 + rng.nextDouble() * 3.5;

            tgc.setFill(Color.rgb(0, 0, 0, 0.4));
            tgc.fillOval(px + 1, py + 1, size, size * 0.6);

            double tone = rng.nextDouble();
            if (tone > 0.6)
                tgc.setFill(Color.web("#878787fd"));
            else if (tone > 0.2)
                tgc.setFill(Color.web("#5c5b5b"));
            else
                tgc.setFill(Color.web("#756c5c"));

            tgc.fillOval(px, py, size, size * 0.6);

            tgc.setFill(Color.web("#FFFFFF", 0.15));
            tgc.fillOval(px + size / 4, py + size / 10, size / 2, size / 4);
        }

        javafx.scene.SnapshotParameters p = new javafx.scene.SnapshotParameters();
        p.setFill(Color.TRANSPARENT);
        return temp.snapshot(p, null);
    }

    private Image createSingleTileImage(Color baseColor, int seed, boolean hasGrass, boolean hasPebbles,
            boolean hasSeedling, Color detailColor) {
        int w = TILE_SIZE * 2;
        int h = TILE_SIZE + 15;
        Canvas temp = new Canvas(w, h);
        GraphicsContext tgc = temp.getGraphicsContext2D();
        double off = 10;
        double[] xs = { TILE_SIZE, TILE_SIZE * 2, TILE_SIZE, 0 };
        double[] ys = { off, TILE_SIZE / 2.0 + off, TILE_SIZE + off, TILE_SIZE / 2.0 + off };

        tgc.setFill(baseColor);
        tgc.fillPolygon(xs, ys, 4);

        java.util.Random rng = new java.util.Random(seed);

        if (hasPebbles) {
            int pebbleCount = 2 + rng.nextInt(5);
            for (int i = 0; i < pebbleCount; i++) {
                double px = 4 + rng.nextDouble() * (TILE_SIZE * 1.3);
                double py = off + 2 + rng.nextDouble() * (TILE_SIZE / 2.0);
                double size = 0.8 + rng.nextDouble() * 1.8;
                tgc.setFill(Color.gray(0.15 + rng.nextDouble() * 0.2));
                tgc.fillOval(px, py, size, size * 0.6);
                tgc.setFill(Color.gray(0.5, 0.2));
                tgc.fillOval(px + (size * 0.2), py, size * 0.3, size * 0.3);
            }
        }

        if (hasSeedling) {
            double cx = TILE_SIZE;
            double cy = TILE_SIZE / 2.0 + off + 3;
            double hue = 95 + rng.nextDouble() * 40;
            double sat = 0.4 + rng.nextDouble() * 0.3;
            double bright = 0.3 + rng.nextDouble() * 0.2;
            tgc.setStroke(Color.hsb(hue, sat, bright));
            tgc.setLineWidth(1.0 + rng.nextDouble() * 0.4);
            tgc.setLineCap(StrokeLineCap.ROUND);

            double len1 = 2.5 + rng.nextDouble() * 2.5;
            double ang1 = Math.toRadians(-100 - rng.nextDouble() * 35);
            double x1End = cx + Math.cos(ang1) * len1;
            double y1End = cy + Math.sin(ang1) * len1;

            tgc.beginPath();
            tgc.moveTo(cx, cy);
            tgc.quadraticCurveTo(cx - 2, cy - len1 / 3, x1End, y1End);
            tgc.stroke();

            double len2 = 2.5 + rng.nextDouble() * 2.5;
            double ang2 = Math.toRadians(-80 + rng.nextDouble() * 35);
            double x2End = cx + Math.cos(ang2) * len2;
            double y2End = cy + Math.sin(ang2) * len2;

            tgc.beginPath();
            tgc.moveTo(cx, cy);
            tgc.quadraticCurveTo(cx + 2, cy - len2 / 3, x2End, y2End);
            tgc.stroke();
        }

        if (hasGrass && detailColor != null) {
            int count = 6 + rng.nextInt(6);
            for (int i = 0; i < count; i++) {
                double rx = 4 + rng.nextDouble() * (TILE_SIZE * 1.5),
                        ry = off + 2 + rng.nextDouble() * (TILE_SIZE / 2.0);
                double gh = 3 + rng.nextDouble() * 5, bend = -2 + rng.nextDouble() * 4;
                tgc.setStroke(detailColor.deriveColor(rng.nextDouble() * 10 - 5, 1, 0.8 + rng.nextDouble() * 0.4, 1));
                tgc.setLineWidth(1.0 + rng.nextDouble() * 0.5);
                tgc.strokeLine(rx, ry, rx + bend, ry - gh);
            }
        }

        javafx.scene.SnapshotParameters p = new javafx.scene.SnapshotParameters();
        p.setFill(Color.TRANSPARENT);
        return temp.snapshot(p, null);
    }

    private void toggleCouping() {
        if (isAttached)
            isAttached = false;
        else {
            double backX = tractorX - Math.cos(Math.toRadians(angle)) * 30;
            double backY = tractorY - Math.sin(Math.toRadians(angle)) * 30;
            if (Math.sqrt(Math.pow(backX - trailerX, 2) + Math.pow(backY - trailerY, 2)) < 35)
                isAttached = true;
        }
    }

    private void update(double dt) {
        if (activeKeys.contains(KeyCode.W))
            currentSpeed = Math.min(currentSpeed + ACCELERATION * dt * 60, MAX_SPEED);
        else if (activeKeys.contains(KeyCode.S)) {
            if (currentSpeed > 0)
                currentSpeed = Math.max(currentSpeed - BRAKE_FORCE * dt * 60, 0);
            else
                currentSpeed = Math.max(currentSpeed - ACCELERATION, -MAX_SPEED / 3.0);
        } else {
            currentSpeed *= (1 - FRICTION * dt * 60);
            if (Math.abs(currentSpeed) < 0.005)
                currentSpeed = 0;
        }

        if (Math.abs(currentSpeed) > 0.01) {
            double dir = currentSpeed > 0 ? 1 : -1;
            double turn = Math.min(Math.abs(currentSpeed) * 1.2, BASE_ROTATION);
            if (activeKeys.contains(KeyCode.A))
                angle -= turn * dir * dt * 60;
            if (activeKeys.contains(KeyCode.D))
                angle += turn * dir * dt * 60;
        }

        double diff = angle - smoothedAngle;
        while (diff < -180)
            diff += 360;
        while (diff > 180)
            diff -= 360;
        smoothedAngle += diff * 0.25;

        double nextX = tractorX + Math.cos(Math.toRadians(angle)) * currentSpeed * dt * 60;
        double nextY = tractorY + Math.sin(Math.toRadians(angle)) * currentSpeed * dt * 60;

        double limit = (MAP_SIZE - 1) * TILE_SIZE;

        double shedMinX = SHED_X * TILE_SIZE;
        double shedMaxX = (SHED_X + SHED_W) * TILE_SIZE;
        double shedMinY = SHED_Y * TILE_SIZE;
        double shedMaxY = (SHED_Y + SHED_H) * TILE_SIZE;
        boolean collision = false;

        if (nextX > shedMinX && nextX < shedMaxX && nextY > shedMinY && nextY < shedMaxY) {
            collision = true;
            currentSpeed = -currentSpeed * 0.5;
        }

        if (!collision && nextX >= 0 && nextX <= limit && nextY >= 0 && nextY <= limit) {
            tractorX = nextX;
            tractorY = nextY;
        } else if (!collision) {
            currentSpeed *= 0.5;
        }

        if (isAttached) {
            double dx = tractorX - trailerX, dy = tractorY - trailerY, dist = Math.sqrt(dx * dx + dy * dy);
            if (dist != TRAILER_DISTANCE) {
                double f = dist - TRAILER_DISTANCE;
                trailerX += Math.cos(Math.atan2(dy, dx)) * f;
                trailerY += Math.sin(Math.atan2(dy, dx)) * f;
                double adiff = Math.toDegrees(Math.atan2(dy, dx)) - trailerAngle;
                while (adiff < -180)
                    adiff += 360;
                while (adiff > 180)
                    adiff -= 360;
                if (Math.abs(currentSpeed) > 0.05)
                    trailerAngle += adiff * (currentSpeed > 0 ? 0.15 : 0.4);
            }

            int tx = (int) (trailerX / TILE_SIZE), ty = (int) (trailerY / TILE_SIZE);
            if (tx != lastPlowCol || ty != lastPlowRow) {
                lastPlowCol = tx;
                lastPlowRow = ty;
                for (int i = -3; i <= 3; i++) {
                    for (int j = -3; j <= 3; j++) {
                        int nx = tx + i, ny = ty + j;
                        if (ny >= 5 && ny < MAP_SIZE - 5 && nx >= 17 && nx < MAP_SIZE - 5) {
                            if (farmMap[ny][nx] == TERRAIN_GRAVEL_ROAD)
                                continue;
                            if (currentToolType == TOOL_PLOW) {
                                if (farmMap[ny][nx] == TERRAIN_GRASS) {
                                    farmMap[ny][nx] = TERRAIN_DIRT;
                                    miniMapImage.getPixelWriter().setColor(nx, ny, Color.web("#5d3a1a"));
                                }
                            } else if (currentToolType == TOOL_PLANTER) {
                                if (farmMap[ny][nx] == TERRAIN_DIRT) {
                                    farmMap[ny][nx] = TERRAIN_PLANTED;
                                    miniMapImage.getPixelWriter().setColor(nx, ny, Color.web("#44aa44"));
                                }
                            }
                        }
                    }
                }
            }
        }
        cameraX = (tractorX - tractorY) - WIDTH / 2.0;
        cameraY = (tractorX + tractorY) / 2.0 - HEIGHT / 2.0;
    }

    private double getNoise(int x, int y) {
        int n = x * 45291 + y * 94607;
        n = (n << 13) ^ n;
        return (1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0 + 1.0) / 2.0;
    }

    private void render(GraphicsContext gc) {
        gc.setFill(Color.web("#0d1a0a"));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.save();
        gc.translate(-cameraX, -cameraY);

        int cCol = (int) (tractorX / TILE_SIZE), cRow = (int) (tractorY / TILE_SIZE), rad = 65;

        for (int r = cRow - rad; r <= cRow + rad; r++) {
            for (int c = cCol - rad; c <= cCol + rad; c++) {
                boolean underShed = c >= SHED_X && c < SHED_X + SHED_W &&
                        r >= SHED_Y && r < SHED_Y + SHED_H;
                double ix = (c * TILE_SIZE - r * TILE_SIZE);
                double iy = (c * TILE_SIZE + r * TILE_SIZE) / 2.0;
                if (ix > cameraX - 150 && ix < cameraX + WIDTH + 150 && iy > cameraY - 150
                        && iy < cameraY + HEIGHT + 150) {
                    Image img = null;
                    if (c >= 0 && c < 12) {
                        img = (c < 2 || c >= 10) ? imgRoadBorder : imgRoad;
                    } else if (r >= 0 && r < MAP_SIZE && c >= 12 && c < MAP_SIZE) {
                        if (farmMap[r][c] == TERRAIN_GRAVEL_ROAD) {
                            img = gravelVariants[noiseMap[r][c] % 16];
                        } else {
                            int margin = 5;
                            if (c < 12 + margin || c >= MAP_SIZE - margin || r < margin || r >= MAP_SIZE - margin) {
                                img = darkGrassVariants[noiseMap[r][c]];
                            } else {
                                if (farmMap[r][c] == TERRAIN_PLANTED) {
                                    img = seedlingVariants[noiseMap[r][c]];
                                } else if (farmMap[r][c] == TERRAIN_DIRT) {
                                    if (noiseMap[r][c] > 35) {
                                        img = plowedPebbleVariants[noiseMap[r][c]];
                                    } else {
                                        img = imgPlowed;
                                    }
                                } else if (farmMap[r][c] == TERRAIN_LIGHT_DIRT) {

                                    int n = noiseMap[r][c];

                                    img = imgPlowed;

                                    if (n > 30) {
                                        img = plowedPebbleVariants[n];
                                    }
                                } else {
                                    img = grassVariants[noiseMap[r][c]];
                                }
                            }
                        }
                    } else {
                        img = darkGrassVariants[(int) (getNoise(r, c) * 63)];
                    }
                    if (img != null) {
                        gc.drawImage(img, Math.floor(ix - TILE_SIZE), Math.floor(iy - 10));

                        if (r >= 0 && r < MAP_SIZE && c >= 0 && c < MAP_SIZE) {

                            boolean nearGravel = hasNeighbor(r, c, TERRAIN_GRAVEL_ROAD);
                            boolean nearGrass = hasNeighbor(r, c, TERRAIN_GRASS);

                            if (farmMap[r][c] == TERRAIN_LIGHT_DIRT && nearGravel) {
                                gc.setFill(Color.rgb(120, 110, 95, 0.28));
                                drawIsoOverlay(gc, ix, iy);

                                double seed = noiseMap[r][c] / 63.0;
                                if (seed > 0.6) {
                                    gc.setFill(Color.rgb(90, 90, 90, 0.35));
                                    gc.fillOval(ix - 3, iy + 2, 2.2, 1.4);
                                }
                            }

                            if (farmMap[r][c] == TERRAIN_LIGHT_DIRT && nearGrass) {
                                gc.setFill(Color.rgb(105, 130, 95, 0.22));
                                drawIsoOverlay(gc, ix, iy);
                            }
                        }
                    }

                }
            }
        }

        drawRoadLine(gc, 5.8, Color.web("#f1c40f"), 2);
        drawRoadLine(gc, 6.2, Color.web("#f1c40f"), 2);
        drawRoadLine(gc, 2.2, Color.WHITE, 1.5);

        double tractorIsoY = (tractorX + tractorY) / 2.0;

        drawTrees(gc, cCol, cRow, rad, true, tractorIsoY);

        double shedBaseIsoY = (SHED_X * TILE_SIZE + (SHED_Y + SHED_H) * TILE_SIZE) / 2.0;

        if (tractorIsoY >= shedBaseIsoY) {
            drawShedImage(gc);
        }

        if (isAttached) {
            double xt = (tractorX - tractorY), yt = (tractorX + tractorY) / 2.0 - 10;
            double fx = trailerX + Math.cos(Math.toRadians(trailerAngle)) * 5;
            double fy = trailerY + Math.sin(Math.toRadians(trailerAngle)) * 5;
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(3);
            gc.strokeLine(xt, yt, (fx - fy), (fx + fy) / 2.0);
        }

        Color machineColor;
        if (currentToolType == TOOL_PLOW) {
            machineColor = Color.web("#1a4a7a");
        } else {
            machineColor = Color.web("#2d7a1a");
        }
        renderMachineIso(gc, trailerX, trailerY, trailerAngle, machineColor);

        renderIsoTractor(gc, (tractorX - tractorY), tractorIsoY);

        if (tractorIsoY < shedBaseIsoY) {
            drawShedImage(gc);
        }

        drawTrees(gc, cCol, cRow, rad, false, tractorIsoY);

        gc.restore();
        renderMiniMap(gc);
        renderSpeedometer(gc);
    }

    private int distanceToTerrain(int r, int c, int terrainType, int maxDist) {
        for (int d = 1; d <= maxDist; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    int nr = r + dy;
                    int nc = c + dx;
                    if (nr >= 0 && nr < MAP_SIZE && nc >= 0 && nc < MAP_SIZE) {
                        if (farmMap[nr][nc] == terrainType) {
                            return d;
                        }
                    }
                }
            }
        }
        return 999;
    }

    private void drawIsoOverlay(GraphicsContext gc, double ix, double iy) {
        gc.fillPolygon(
                new double[] { ix, ix + TILE_SIZE, ix, ix - TILE_SIZE },
                new double[] { iy, iy + TILE_SIZE / 2.0, iy + TILE_SIZE, iy + TILE_SIZE / 2.0 },
                4);
    }

    private boolean hasNeighbor(int r, int c, int type) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0)
                    continue;

                int nr = r + dy;
                int nc = c + dx;

                if (nr >= 0 && nr < MAP_SIZE && nc >= 0 && nc < MAP_SIZE) {
                    if (farmMap[nr][nc] == type)
                        return true;
                }
            }
        }
        return false;
    }

    private void drawShedImage(GraphicsContext gc) {
        if (shedImage == null)
            return;

        double sx = (SHED_X * TILE_SIZE - SHED_Y * TILE_SIZE);
        double ex = ((SHED_X + SHED_W) * TILE_SIZE - (SHED_Y + SHED_H) * TILE_SIZE);
        double ey = ((SHED_X + SHED_W) * TILE_SIZE + (SHED_Y + SHED_H) * TILE_SIZE) / 2.0;

        double baseX = (sx + ex) / 2.0;
        double baseY = ey;

        double originalW = shedImage.getWidth();
        double originalH = shedImage.getHeight();

        double targetW = (SHED_W * TILE_SIZE + SHED_H * TILE_SIZE) * 0.75;
        double scaleFactor = targetW / originalW;
        double targetH = originalH * scaleFactor;

        double drawX = baseX - targetW / 2.0;
        double drawY = baseY - targetH + (10 * scaleFactor);

        gc.save();
        gc.translate(drawX + targetW * 0.52, drawY + targetH * 0.90);
        gc.transform(1, 0, -0.8, 0.5, 0, 0);
        gc.setGlobalAlpha(0.28);

        javafx.scene.effect.ColorAdjust mono = new javafx.scene.effect.ColorAdjust();
        mono.setBrightness(-1.0);
        gc.setEffect(mono);

        gc.drawImage(
                shedImage,
                -targetW / 2.0,
                -targetH,
                targetW,
                targetH);

        gc.setEffect(null);
        gc.setGlobalAlpha(1.0);
        gc.restore();

        gc.drawImage(
                shedImage,
                drawX,
                drawY,
                targetW,
                targetH);
    }

    private void drawTrees(GraphicsContext gc, int cCol, int cRow, int rad, boolean behind, double tractorY) {
        if (treeSheet == null)
            return;

        for (int r = cRow - rad; r <= cRow + rad; r++) {
            for (int c = cCol - rad; c <= cCol + rad; c++) {

                boolean isForestArea = !(c >= 0 && c < 12) &&
                        !(r >= 0 && r < MAP_SIZE && c >= 12 && c < MAP_SIZE);

                if (!isForestArea)
                    continue;

                double nVal = getNoise(r, c);

                if (nVal <= 0.58)
                    continue;

                double ix = (c * TILE_SIZE - r * TILE_SIZE);
                double iy = (c * TILE_SIZE + r * TILE_SIZE) / 2.0;

                if (behind ? (iy <= tractorY) : (iy > tractorY)) {

                    if (ix > cameraX - 150 && ix < cameraX + WIDTH + 150 &&
                            iy > cameraY - 150 && iy < cameraY + HEIGHT + 150) {

                        double tw = TREE_W, th = TREE_H, scale = 0.38;
                        double dw = tw * scale, dh = th * scale;

                        double ox = (nVal * 8) - 4;
                        double oy = (Math.sin(r * 0.5) * 3);

                        gc.setFill(Color.rgb(0, 0, 0, 0.3));
                        gc.fillOval(ix - dw / 3.0 + ox + 5, iy - 5 + oy, dw * 0.8, dh * 0.2);

                        int treeIdx = Math.abs((r * 13 + c * 7) % 8);

                        gc.drawImage(
                                treeSheet,
                                (treeIdx % 4) * tw,
                                (treeIdx / 4) * th,
                                tw, th,
                                ix - dw / 2.0 + ox,
                                iy - dh + 5 + oy,
                                dw, dh);
                    }
                }
            }
        }
    }

    private void drawRoadLine(GraphicsContext gc, double colPos, Color color, double width) {
        gc.setStroke(color);
        gc.setLineWidth(width);

        double extension = MAP_SIZE * 10;
        double xs = (colPos * TILE_SIZE) + (extension * TILE_SIZE);
        double ys = ((colPos * TILE_SIZE) - (extension * TILE_SIZE)) / 2.0;
        double xe = (colPos * TILE_SIZE) - (extension * TILE_SIZE);
        double ye = ((colPos * TILE_SIZE) + (extension * TILE_SIZE)) / 2.0;

        gc.strokeLine(xs, ys + TILE_SIZE / 2.0, xe, ye + TILE_SIZE / 2.0);
    }

    private void renderMachineIso(GraphicsContext gc, double tx, double ty, double tA, Color bodyColor) {
        double ix = tx - ty, iy = (tx + ty) / 2.0;
        gc.save();
        gc.translate(ix, iy);
        gc.rotate(tA + 45);

        gc.setFill(bodyColor);
        gc.fillRect(-7, -45, 12, 90);
        gc.setFill(Color.web("#000000", 0.2));
        gc.fillRect(-7, -45, 4, 90);
        for (int i = -40; i <= 40; i += 13) {
            gc.setFill(Color.web("#222222"));
            gc.fillRect(-10, i - 1, 6, 2);
            gc.setFill(Color.web("#777777"));
            gc.fillOval(-14, i - 4, 8, 8);
            gc.setFill(Color.web("#eeeeee", 0.5));
            gc.fillOval(-12, i - 2, 3, 3);

            gc.setFill(bodyColor.darker());
            gc.fillRect(-8, i - 4, 5, 8);
        }
        gc.setFill(Color.web("#222222"));
        gc.fillOval(-5, -51, 10, 6);
        gc.fillOval(-5, 45, 10, 6);
        gc.restore();
    }

    private void renderIsoTractor(GraphicsContext gc, double x, double y) {
        if (tractorSheet == null)
            return;

        double fa = (90 - ((smoothedAngle % 360 + 360) % 360) + 360) % 360;
        int idx = (int) Math.floor((fa + 7.5) / 15.0) % 24;
        double dw = SPRITE_W * TRACTOR_SCALE;
        double dh = SPRITE_H * TRACTOR_SCALE;

        gc.save();

        gc.translate(x + 2, y + (dh * 0.02));
        gc.transform(1, 0, -0.8, 0.5, 0, 0);
        gc.setGlobalAlpha(0.3);
        javafx.scene.effect.ColorAdjust monochrome = new javafx.scene.effect.ColorAdjust();
        monochrome.setBrightness(-1.0);
        gc.setEffect(monochrome);
        gc.drawImage(tractorSheet,
                (idx % 6) * SPRITE_W, (idx / 6) * SPRITE_H, SPRITE_W, SPRITE_H,
                -dw / 2.0, -dh * 0.85, dw, dh);
        gc.setEffect(null);
        gc.setGlobalAlpha(1.0);
        gc.restore();

        gc.save();
        gc.translate(x, y);
        gc.drawImage(tractorSheet,
                (idx % 6) * SPRITE_W, (idx / 6) * SPRITE_H, SPRITE_W, SPRITE_H,
                -dw / 2.0, -dh * 0.85, dw, dh);
        gc.restore();
    }

    private void renderMiniMap(GraphicsContext gc) {
        double sz = 140, mx = WIDTH - sz - 20, my = 20, cx = mx + sz / 2, cy = my + sz / 2;
        gc.setFill(Color.web("#111111", 0.85));
        gc.fillOval(mx, my, sz, sz);
        gc.save();
        gc.beginPath();
        gc.arc(cx, cy, sz / 2, sz / 2, 0, 360);
        gc.clip();
        gc.drawImage(miniMapImage, (tractorX / TILE_SIZE) - miniMapVR, (tractorY / TILE_SIZE) - miniMapVR,
                miniMapVR * 2, miniMapVR * 2, mx, my, sz, sz);
        gc.setFill(Color.YELLOW);
        gc.fillOval(cx - 3, cy - 3, 6, 6);
        gc.restore();
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(mx, my, sz, sz);

        drawZoomButton(gc, 14, cx + 50, cy + 50, "+");
        drawZoomButton(gc, 20, cx + 20, cy + 65, "-");
    }

    private void renderSpeedometer(GraphicsContext gc) {
        double cx = 120, cy = HEIGHT - 50, r = 90;
        gc.setFill(Color.web("#111111", 0.9));
        gc.fillArc(cx - r, cy - r, r * 2, r * 2, 0, 180, ArcType.ROUND);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);

        for (int i = 0; i <= 180; i += 30) {
            double rad = Math.toRadians(180 - i);
            gc.strokeLine(cx + Math.cos(rad) * (r - 5), cy - Math.sin(rad) * (r - 5), cx + Math.cos(rad) * r,
                    cy - Math.sin(rad) * r);
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font("Arial", 10));
            gc.fillText(String.valueOf((int) ((i / 180.0) * MAX_SPEED_KMH)), cx + Math.cos(rad) * (r - 18) - 5,
                    cy - Math.sin(rad) * (r - 18) + 5);
        }
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(-180 + (180 * (Math.abs(currentSpeed) / MAX_SPEED)));
        gc.setStroke(Color.RED);
        gc.setLineWidth(3);
        gc.strokeLine(10, 0, r - 12, 0);
        gc.restore();
    }

    public static void main(String[] args) {
        launch(args);
    }
}