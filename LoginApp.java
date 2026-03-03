// "C:\\001Files\\fon.jpg"; // Путь к изображению фона
import javax.swing.*;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.Timer;

// Класс для хранения географических данных
class GeoData {
    String name;
    double latitude;
    double longitude;
    double altitude;

    public GeoData(String name, double latitude, double longitude, double altitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    @Override
    public String toString() {
        return String.format("%s: B=%.4f° L=%.4f° H=%.0fм", name, latitude, longitude, altitude);
    }
}

// Класс для хранения точки трека в геодезических координатах ПЗ-90.02
class TrackPoint {
    double latitude;  // B - широта в радианах
    double longitude; // L - долгота в радианах
    double altitude;  // H - высота в метрах
    long timestamp;

    public TrackPoint(double latitude, double longitude, double altitude, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.timestamp = timestamp;
    }
}

// Класс для геодезических вычислений ПЗ-90.02 с поддержкой масштабирования и перемещения
class PZ902Geodesy {
    // Константы ПЗ-90.02
    public static final double EARTH_SEMI_MAJOR_AXIS = 6378136.0;
    public static final double EARTH_FLATTENING = 1.0 / 298.257839303;

    // Базовые границы отображаемой области (в градусах)
    private double baseMinLat = 54.0;
    private double baseMaxLat = 56.0;
    private double baseMinLon = 36.0;
    private double baseMaxLon = 38.0;

    // Текущие границы с учётом масштабирования и перемещения
    private double minLat, maxLat, minLon, maxLon;

    // Коэффициент масштабирования (1.0 - исходный масштаб, >1 - увеличение, <1 - уменьшение)
    private double zoomFactor = 1.0;

    // Привязка к конкретным координатам (например, аэродром)
    private double targetLat = 55.0;  // Целевая широта (аэродром)
    private double targetLon = 37.0;  // Целевая долгота (аэродром)
    private String targetName = "Аэродром";
    private boolean isLockedToTarget = false; // Флаг привязки

    public PZ902Geodesy() {
        resetZoom();
    }

    // Сброс масштаба к исходному
    public void resetZoom() {
        minLat = baseMinLat;
        maxLat = baseMaxLat;
        minLon = baseMinLon;
        maxLon = baseMaxLon;
        zoomFactor = 1.0;
    }

    // Привязка к целевым координатам
    public void lockToTarget() {
        isLockedToTarget = true;
        centerOnTarget();
    }

    // Открепление от целевых координат
    public void unlockFromTarget() {
        isLockedToTarget = false;
    }

    // Центрирование карты на целевых координатах
    public void centerOnTarget() {
        double lonRange = maxLon - minLon;
        double latRange = maxLat - minLat;

        minLon = targetLon - lonRange / 2;
        maxLon = targetLon + lonRange / 2;
        minLat = targetLat - latRange / 2;
        maxLat = targetLat + latRange / 2;
    }

    // Установка границ карты по данным из файла
    public void setBoundsFromGeoData(List<GeoData> dataList, double margin) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        double minLat = 90;
        double maxLat = -90;
        double minLon = 180;
        double maxLon = -180;

        for (GeoData data : dataList) {
            minLat = Math.min(minLat, data.latitude);
            maxLat = Math.max(maxLat, data.latitude);
            minLon = Math.min(minLon, data.longitude);
            maxLon = Math.max(maxLon, data.longitude);
        }

        // Добавляем отступы
        double latRange = maxLat - minLat;
        double lonRange = maxLon - minLon;

        this.minLat = minLat - latRange * margin;
        this.maxLat = maxLat + latRange * margin;
        this.minLon = minLon - lonRange * margin;
        this.maxLon = maxLon + lonRange * margin;

        // Обновляем zoomFactor
        zoomFactor = (baseMaxLon - baseMinLon) / (this.maxLon - this.minLon);
    }

    // Установка целевых координат
    public void setTargetCoordinates(double latitude, double longitude, String name) {
        this.targetLat = latitude;
        this.targetLon = longitude;
        this.targetName = name;
        if (isLockedToTarget) {
            centerOnTarget();
        }
    }

    // Перемещение карты (панорамирование)
    public void pan(double deltaX, double deltaY, int panelWidth, int panelHeight) {
        if (isLockedToTarget) {
            return; // Не перемещаем, если есть привязка
        }

        // Преобразуем перемещение в пикселях в изменение градусов
        double lonRange = maxLon - minLon;
        double latRange = maxLat - minLat;

        double deltaLon = -deltaX * lonRange / panelWidth;
        double deltaLat = deltaY * latRange / panelHeight;

        minLon += deltaLon;
        maxLon += deltaLon;
        minLat += deltaLat;
        maxLat += deltaLat;

        // Ограничиваем, чтобы не уйти слишком далеко
        double maxLonRange = (baseMaxLon - baseMinLon) / 0.01;
        double maxLatRange = (baseMaxLat - baseMinLat) / 0.01;

        if (maxLon - minLon > maxLonRange) {
            minLon -= deltaLon;
            maxLon -= deltaLon;
        }

        if (maxLat - minLat > maxLatRange) {
            minLat -= deltaLat;
            maxLat -= deltaLat;
        }
    }

    // Изменение масштаба
    public void zoom(double delta, int mouseX, int mouseY, int panelWidth, int panelHeight) {
        // Определяем коэффициент изменения масштаба
        double scaleFactor = 1.1;
        if (delta < 0) {
            scaleFactor = 1.0 / scaleFactor; // Уменьшение
        }

        // Сохраняем старые границы
        double oldMinLat = minLat;
        double oldMaxLat = maxLat;
        double oldMinLon = minLon;
        double oldMaxLon = maxLon;

        // Вычисляем координаты точки под курсором мыши
        double mouseLat = oldMaxLat - (mouseY / (double)panelHeight) * (oldMaxLat - oldMinLat);
        double mouseLon = oldMinLon + (mouseX / (double)panelWidth) * (oldMaxLon - oldMinLon);

        // Вычисляем новые границы с сохранением пропорций относительно курсора
        double leftDist = mouseLon - oldMinLon;
        double rightDist = oldMaxLon - mouseLon;
        double topDist = oldMaxLat - mouseLat;
        double bottomDist = mouseLat - oldMinLat;

        // Новые границы
        minLon = mouseLon - leftDist / scaleFactor;
        maxLon = mouseLon + rightDist / scaleFactor;
        minLat = mouseLat - bottomDist / scaleFactor;
        maxLat = mouseLat + topDist / scaleFactor;

        // Ограничиваем максимальное увеличение (минимальный диапазон)
        double minRange = 0.02;
        if (maxLon - minLon < minRange || maxLat - minLat < minRange) {
            minLat = oldMinLat;
            maxLat = oldMaxLat;
            minLon = oldMinLon;
            maxLon = oldMaxLon;
        }

        // Ограничиваем максимальное уменьшение
        double maxRange = (baseMaxLon - baseMinLon) / 0.01;
        if (maxLon - minLon > maxRange || maxLat - minLat > maxRange) {
            minLat = oldMinLat;
            maxLat = oldMaxLat;
            minLon = oldMinLon;
            maxLon = oldMaxLon;
        }

        // Обновляем коэффициент масштабирования
        zoomFactor = (baseMaxLon - baseMinLon) / (maxLon - minLon);

        // Если есть привязка, центрируем на цели после масштабирования
        if (isLockedToTarget) {
            centerOnTarget();
        }
    }

    // Преобразование геодезических координат в экранные
    public double[] geodeticToScreen(double latitudeRad, double longitudeRad, int panelWidth, int panelHeight) {
        double latDeg = Math.toDegrees(latitudeRad);
        double lonDeg = Math.toDegrees(longitudeRad);

        double screenX = (lonDeg - minLon) / (maxLon - minLon) * panelWidth;
        double screenY = (maxLat - latDeg) / (maxLat - minLat) * panelHeight;

        return new double[]{screenX, screenY};
    }

    // Преобразование экранных координат в геодезические
    public double[] screenToGeodetic(int screenX, int screenY, int panelWidth, int panelHeight) {
        double lonDeg = minLon + (screenX / (double)panelWidth) * (maxLon - minLon);
        double latDeg = maxLat - (screenY / (double)panelHeight) * (maxLat - minLat);

        return new double[]{latDeg, lonDeg};
    }

    // Получить экранные координаты для точки сетки по градусам
    public double[] getGridLinePoint(double latDeg, double lonDeg, int panelWidth, int panelHeight) {
        double screenX = (lonDeg - minLon) / (maxLon - minLon) * panelWidth;
        double screenY = (maxLat - latDeg) / (maxLat - minLat) * panelHeight;
        return new double[]{screenX, screenY};
    }

    // Геттеры и сеттеры
    public double getMinLat() { return minLat; }
    public double getMaxLat() { return maxLat; }
    public double getMinLon() { return minLon; }
    public double getMaxLon() { return maxLon; }
    public double getZoomFactor() { return zoomFactor; }
    public double getTargetLat() { return targetLat; }
    public double getTargetLon() { return targetLon; }
    public String getTargetName() { return targetName; }
    public boolean isLockedToTarget() { return isLockedToTarget; }
}

// Интерфейс источника данных
interface SensorDataSource {
    String getName();
    List<TrackPoint> getTrackData();
    void startSimulation();
    void stopSimulation();
}

// Источник данных - симулятор радара
class RadarDataSource implements SensorDataSource {
    private List<TrackPoint> trackData = new ArrayList<>();
    private Timer simulationTimer;
    private Random random = new Random();
    private double currentLat = Math.toRadians(55.0);
    private double currentLon = Math.toRadians(37.0);
    private double currentAlt = 10000.0;
    private double angle = 0;

    public RadarDataSource() {
        startSimulation();
    }

    @Override
    public String getName() {
        return "Радар (ПЗ-90.02)";
    }

    @Override
    public List<TrackPoint> getTrackData() {
        return new ArrayList<>(trackData);
    }

    @Override
    public void startSimulation() {
        simulationTimer = new Timer(500, e -> generateTrackPoint());
        simulationTimer.start();
    }

    @Override
    public void stopSimulation() {
        if (simulationTimer != null) {
            simulationTimer.stop();
        }
    }

    private void generateTrackPoint() {
        angle += 0.05;
        double radius = 0.8;

        currentLat = Math.toRadians(55.0 + radius * Math.cos(angle));
        currentLon = Math.toRadians(37.0 + radius * Math.sin(angle));
        currentAlt = 10000.0 + 1000 * Math.sin(angle * 2);

        trackData.add(new TrackPoint(currentLat, currentLon, currentAlt, System.currentTimeMillis()));

        if (trackData.size() > 100) {
            trackData.remove(0);
        }
    }
}

// Источник данных - симулятор GPS
class GPSDataSource implements SensorDataSource {
    private List<TrackPoint> trackData = new ArrayList<>();
    private Timer simulationTimer;
    private Random random = new Random();
    private double currentLat = Math.toRadians(54.2);
    private double currentLon = Math.toRadians(36.2);
    private double currentAlt = 8000.0;

    public GPSDataSource() {
        startSimulation();
    }

    @Override
    public String getName() {
        return "GPS (ПЗ-90.02)";
    }

    @Override
    public List<TrackPoint> getTrackData() {
        return new ArrayList<>(trackData);
    }

    @Override
    public void startSimulation() {
        simulationTimer = new Timer(300, e -> generateTrackPoint());
        simulationTimer.start();
    }

    @Override
    public void stopSimulation() {
        if (simulationTimer != null) {
            simulationTimer.stop();
        }
    }

    private void generateTrackPoint() {
        currentLat += 0.005;
        currentLon += 0.008;
        currentAlt += random.nextInt(100) - 50;

        if (currentLat > Math.toRadians(55.8) || currentLon > Math.toRadians(37.8)) {
            currentLat = Math.toRadians(54.2);
            currentLon = Math.toRadians(36.2);
        }

        trackData.add(new TrackPoint(currentLat, currentLon, currentAlt, System.currentTimeMillis()));

        if (trackData.size() > 100) {
            trackData.remove(0);
        }
    }
}

// Источник данных - ADS-B симулятор
class ADSBSource implements SensorDataSource {
    private List<TrackPoint> trackData = new ArrayList<>();
    private Timer simulationTimer;
    private Random random = new Random();
    private double currentLat = Math.toRadians(55.5);
    private double currentLon = Math.toRadians(36.5);
    private double currentAlt = 9000.0;
    private double latSpeed = 0.01;
    private double lonSpeed = 0.015;

    public ADSBSource() {
        startSimulation();
    }

    @Override
    public String getName() {
        return "ADS-B (ПЗ-90.02)";
    }

    @Override
    public List<TrackPoint> getTrackData() {
        return new ArrayList<>(trackData);
    }

    @Override
    public void startSimulation() {
        simulationTimer = new Timer(200, e -> generateTrackPoint());
        simulationTimer.start();
    }

    @Override
    public void stopSimulation() {
        if (simulationTimer != null) {
            simulationTimer.stop();
        }
    }

    private void generateTrackPoint() {
        currentLat += latSpeed;
        currentLon += lonSpeed;
        currentAlt += random.nextInt(200) - 100;

        double minLat = 54.1;
        double maxLat = 55.9;
        double minLon = 36.1;
        double maxLon = 37.9;

        if (Math.toDegrees(currentLat) < minLat) {
            currentLat = Math.toRadians(minLat);
            latSpeed = -latSpeed;
        }
        if (Math.toDegrees(currentLat) > maxLat) {
            currentLat = Math.toRadians(maxLat);
            latSpeed = -latSpeed;
        }
        if (Math.toDegrees(currentLon) < minLon) {
            currentLon = Math.toRadians(minLon);
            lonSpeed = -lonSpeed;
        }
        if (Math.toDegrees(currentLon) > maxLon) {
            currentLon = Math.toRadians(maxLon);
            lonSpeed = -lonSpeed;
        }

        trackData.add(new TrackPoint(currentLat, currentLon, currentAlt, System.currentTimeMillis()));

        if (trackData.size() > 100) {
            trackData.remove(0);
        }
    }
}

// Панель для отрисовки трека с поддержкой масштабирования и перемещения
class TrackPanel extends JPanel {
    private List<TrackPoint> trackPoints = new ArrayList<>();
    private List<GeoData> geoDataList = new ArrayList<>();
    private String currentSensor = "Нет данных";
    private Color trackColor = Color.RED;
    private boolean showGrid = true;
    private boolean showCoordinates = true;
    private boolean showGeoPoints = true;
    private PZ902Geodesy geodesy;
    private JLabel zoomLabel;
    private JLabel targetLabel;
    private JLabel fileInfoLabel;

    // Для перетаскивания мышью
    private Point lastMousePosition = null;
    private boolean isDragging = false;

    public TrackPanel() {
        geodesy = new PZ902Geodesy();
        setOpaque(false);

        // Добавляем обработчик колесика мыши с Ctrl
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    int mouseX = e.getX();
                    int mouseY = e.getY();
                    double rotation = e.getPreciseWheelRotation();

                    geodesy.zoom(rotation, mouseX, mouseY, getWidth(), getHeight());

                    updateZoomLabel();
                    updateTargetLabel();
                    repaint();
                }
            }
        });

        // Добавляем обработчики мыши для перетаскивания
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2) {
                    lastMousePosition = e.getPoint();
                    isDragging = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2) {
                    lastMousePosition = null;
                    isDragging = false;
                    setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging && lastMousePosition != null) {
                    Point currentMousePosition = e.getPoint();

                    int deltaX = currentMousePosition.x - lastMousePosition.x;
                    int deltaY = currentMousePosition.y - lastMousePosition.y;

                    geodesy.pan(deltaX, deltaY, getWidth(), getHeight());

                    lastMousePosition = currentMousePosition;

                    updateTargetLabel();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (showCoordinates) {
                    double[] geo = geodesy.screenToGeodetic(e.getX(), e.getY(), getWidth(), getHeight());
                    setToolTipText(String.format("B=%.4f° L=%.4f°", geo[0], geo[1]));
                }
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setText(String.format(" Масштаб: %.2fx (Ctrl+колесо) ", geodesy.getZoomFactor()));
        }
    }

    private void updateTargetLabel() {
        if (targetLabel != null) {
            String lockStatus = geodesy.isLockedToTarget() ? " (привязано)" : "";
            targetLabel.setText(String.format(" Цель: %s B=%.2f° L=%.2f°%s ",
                geodesy.getTargetName(), geodesy.getTargetLat(), geodesy.getTargetLon(), lockStatus));
        }
    }

    private void updateFileInfoLabel() {
        if (fileInfoLabel != null) {
            if (geoDataList.isEmpty()) {
                fileInfoLabel.setText(" Данные не загружены ");
            } else {
                fileInfoLabel.setText(String.format(" Загружено точек: %d ", geoDataList.size()));
            }
        }
    }

    public void setZoomLabel(JLabel label) {
        this.zoomLabel = label;
        updateZoomLabel();
    }

    public void setTargetLabel(JLabel label) {
        this.targetLabel = label;
        updateTargetLabel();
    }

    public void setFileInfoLabel(JLabel label) {
        this.fileInfoLabel = label;
        updateFileInfoLabel();
    }

    public void updateTrack(List<TrackPoint> newPoints, String sensorName) {
        this.trackPoints = newPoints;
        this.currentSensor = sensorName;
        repaint();
    }

    public void setShowGrid(boolean show) {
        this.showGrid = show;
        repaint();
    }

    public void setShowCoordinates(boolean show) {
        this.showCoordinates = show;
        repaint();
    }

    public void setShowGeoPoints(boolean show) {
        this.showGeoPoints = show;
        repaint();
    }

    public void clearTrack() {
        trackPoints.clear();
        repaint();
    }

    public void clearGeoData() {
        geoDataList.clear();
        updateFileInfoLabel();
        repaint();
    }

    public void resetZoom() {
        geodesy.resetZoom();
        updateZoomLabel();
        updateTargetLabel();
        repaint();
    }

    public void lockToTarget() {
        geodesy.lockToTarget();
        updateTargetLabel();
        repaint();
    }

    public void unlockFromTarget() {
        geodesy.unlockFromTarget();
        updateTargetLabel();
        repaint();
    }

    public void setTargetFromCurrentPosition() {
        if (!trackPoints.isEmpty()) {
            TrackPoint last = trackPoints.get(trackPoints.size() - 1);
            geodesy.setTargetCoordinates(Math.toDegrees(last.latitude), Math.toDegrees(last.longitude), "Текущая позиция");
            updateTargetLabel();
            if (geodesy.isLockedToTarget()) {
                geodesy.centerOnTarget();
                repaint();
            }
        }
    }

    public void setTargetFromGeoData(GeoData data) {
        if (data != null) {
            geodesy.setTargetCoordinates(data.latitude, data.longitude, data.name);
            updateTargetLabel();
            if (geodesy.isLockedToTarget()) {
                geodesy.centerOnTarget();
                repaint();
            }
        }
    }

    public void loadGeoDataFromFile(File file) {
        geoDataList.clear();

        try {
            String fileName = file.getName().toLowerCase();
            System.out.println("Загрузка файла: " + file.getAbsolutePath());

            if (fileName.endsWith(".txt")) {
                loadFromTxt(file);
            } else if (fileName.endsWith(".xml")) {
                loadFromXml(file);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Неподдерживаемый формат файла. Используйте .txt или .xml",
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            updateFileInfoLabel();

            if (!geoDataList.isEmpty()) {
                // Автоматически устанавливаем границы карты по загруженным данным
                geodesy.setBoundsFromGeoData(geoDataList, 0.1); // 10% отступ

                // Если есть данные, выбираем первый как цель
                setTargetFromGeoData(geoDataList.get(0));

                JOptionPane.showMessageDialog(this,
                    "Загружено " + geoDataList.size() + " точек\n" +
                    "Первая точка \"" + geoDataList.get(0).name + "\" установлена как цель",
                    "Успех",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Не найдено точек в файле. Проверьте формат данных.",
                    "Предупреждение",
                    JOptionPane.WARNING_MESSAGE);
            }

            repaint();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Ошибка загрузки файла: " + e.getMessage() + "\n" +
                "Проверьте формат файла и кодировку.",
                "Ошибка",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadFromTxt(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Пропускаем пустые строки и комментарии
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

                // Пропускаем строку с заголовком
                if (line.toLowerCase().contains("название") ||
                    line.toLowerCase().contains("широта") ||
                    line.toLowerCase().contains("долгота")) {
                    continue;
                }

                try {
                    // Разделяем по запятой или табуляции
                    String[] parts;
                    if (line.contains(",")) {
                        parts = line.split(",");
                    } else if (line.contains(";")) {
                        parts = line.split(";");
                    } else if (line.contains("\t")) {
                        parts = line.split("\t");
                    } else {
                        parts = line.split("\\s+"); // По пробелам
                    }

                    if (parts.length >= 3) {
                        // Очищаем части от лишних пробелов
                        String name = parts[0].trim();

                        // Заменяем запятые на точки в числах
                        String latStr = parts[1].trim().replace(',', '.');
                        String lonStr = parts[2].trim().replace(',', '.');

                        double lat = Double.parseDouble(latStr);
                        double lon = Double.parseDouble(lonStr);
                        double alt = 0.0;

                        if (parts.length >= 4) {
                            String altStr = parts[3].trim().replace(',', '.');
                            if (!altStr.isEmpty()) {
                                alt = Double.parseDouble(altStr);
                            }
                        }

                        geoDataList.add(new GeoData(name, lat, lon, alt));
                        System.out.println("Загружена точка: " + name + " " + lat + " " + lon);
                    } else {
                        System.err.println("Строка " + lineNumber + " содержит недостаточно данных: " + line);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Ошибка парсинга строки " + lineNumber + ": " + line);
                    System.err.println("Ошибка: " + e.getMessage());
                }
            }
        }
    }

    private void loadFromXml(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            String xml = content.toString();

            // Более надёжный парсинг XML
            String[] points = xml.split("<point>|</point>|<Point>|</Point>");
            for (String point : points) {
                if (point.contains("<name>") || point.contains("<Name>") ||
                    point.contains("<lat>") || point.contains("<Lat>") ||
                    point.contains("<lon>") || point.contains("<Lon>")) {

                    String name = extractXmlTag(point, "name", "Name");
                    String latStr = extractXmlTag(point, "lat", "Lat");
                    String lonStr = extractXmlTag(point, "lon", "Lon");
                    String altStr = extractXmlTag(point, "alt", "Alt");

                    if (!name.isEmpty() && !latStr.isEmpty() && !lonStr.isEmpty()) {
                        try {
                            // Заменяем запятые на точки
                            latStr = latStr.replace(',', '.');
                            lonStr = lonStr.replace(',', '.');
                            altStr = altStr.replace(',', '.');

                            double lat = Double.parseDouble(latStr);
                            double lon = Double.parseDouble(lonStr);
                            double alt = altStr.isEmpty() ? 0.0 : Double.parseDouble(altStr);

                            geoDataList.add(new GeoData(name, lat, lon, alt));
                            System.out.println("Загружена точка: " + name + " " + lat + " " + lon);
                        } catch (NumberFormatException e) {
                            System.err.println("Ошибка парсинга координат: " + point);
                        }
                    }
                }
            }
        }
    }

    private String extractXmlTag(String xml, String... tagNames) {
        for (String tagName : tagNames) {
            String openTag = "<" + tagName + ">";
            String closeTag = "</" + tagName + ">";
            int start = xml.indexOf(openTag);
            int end = xml.indexOf(closeTag);

            if (start < 0) {
                // Пробуем без угловых скобок
                openTag = "<" + tagName;
                closeTag = "</" + tagName + ">";
                start = xml.indexOf(openTag);
                if (start >= 0) {
                    start = xml.indexOf(">", start) + 1;
                    end = xml.indexOf(closeTag);
                }
            }

            if (start >= 0 && end > start) {
                if (start < end) {
                    return xml.substring(start + openTag.length(), end).trim();
                }
            }
        }
        return "";
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Рисуем серую сетку
        if (showGrid) {
            drawGrid(g2d, width, height);
        }

        // Рисуем загруженные географические точки
        if (showGeoPoints && !geoDataList.isEmpty()) {
            drawGeoPoints(g2d, width, height);
        }

        // Рисуем целевую точку
        drawTarget(g2d, width, height);

        // Рисуем трек
        drawTrack(g2d, width, height);

        // Рисуем информацию
        drawInfo(g2d, width, height);
    }

    private void drawGrid(Graphics2D g2d, int width, int height) {
        g2d.setColor(new Color(128, 128, 128, 60));
        g2d.setStroke(new BasicStroke(1));

        double latRange = geodesy.getMaxLat() - geodesy.getMinLat();

        double gridStep;
        if (latRange < 0.1) {
            gridStep = 0.01;
        } else if (latRange < 0.5) {
            gridStep = 0.05;
        } else if (latRange < 1.0) {
            gridStep = 0.1;
        } else if (latRange < 2.0) {
            gridStep = 0.2;
        } else if (latRange < 5.0) {
            gridStep = 0.5;
        } else {
            gridStep = 1.0;
        }

        double firstLat = Math.ceil(geodesy.getMinLat() / gridStep) * gridStep;
        for (double lat = firstLat; lat <= geodesy.getMaxLat(); lat += gridStep) {
            drawParallel(g2d, lat, width, height);
        }

        double firstLon = Math.ceil(geodesy.getMinLon() / gridStep) * gridStep;
        for (double lon = firstLon; lon <= geodesy.getMaxLon(); lon += gridStep) {
            drawMeridian(g2d, lon, width, height);
        }

        g2d.setStroke(new BasicStroke(2));
        g2d.setColor(new Color(100, 100, 100, 80));

        double[] northWest = geodesy.getGridLinePoint(geodesy.getMaxLat(), geodesy.getMinLon(), width, height);
        double[] northEast = geodesy.getGridLinePoint(geodesy.getMaxLat(), geodesy.getMaxLon(), width, height);
        double[] southWest = geodesy.getGridLinePoint(geodesy.getMinLat(), geodesy.getMinLon(), width, height);
        double[] southEast = geodesy.getGridLinePoint(geodesy.getMinLat(), geodesy.getMaxLon(), width, height);

        g2d.drawLine((int)northWest[0], (int)northWest[1], (int)northEast[0], (int)northEast[1]);
        g2d.drawLine((int)southWest[0], (int)southWest[1], (int)southEast[0], (int)southEast[1]);
        g2d.drawLine((int)northWest[0], (int)northWest[1], (int)southWest[0], (int)southWest[1]);
        g2d.drawLine((int)northEast[0], (int)northEast[1], (int)southEast[0], (int)southEast[1]);

        if (showCoordinates) {
            g2d.setColor(new Color(80, 80, 80, 200));
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));

            for (double lat = firstLat; lat <= geodesy.getMaxLat(); lat += gridStep) {
                if (lat > geodesy.getMinLat() && lat < geodesy.getMaxLat()) {
                    double[] pos = geodesy.getGridLinePoint(lat, geodesy.getMinLon(), width, height);
                    if (pos[1] > 20 && pos[1] < height - 20) {
                        g2d.drawString(String.format("%.2f°", lat), 5, (int)pos[1] - 2);
                    }
                }
            }

            for (double lon = firstLon; lon <= geodesy.getMaxLon(); lon += gridStep) {
                if (lon > geodesy.getMinLon() && lon < geodesy.getMaxLon()) {
                    double[] pos = geodesy.getGridLinePoint(geodesy.getMinLat(), lon, width, height);
                    if (pos[0] > 20 && pos[0] < width - 40) {
                        g2d.drawString(String.format("%.2f°", lon), (int)pos[0] - 15, height - 5);
                    }
                }
            }
        }
    }

    private void drawGeoPoints(Graphics2D g2d, int width, int height) {
        for (GeoData data : geoDataList) {
            double[] screen = geodesy.getGridLinePoint(data.latitude, data.longitude, width, height);

            if (screen[0] >= 0 && screen[0] <= width && screen[1] >= 0 && screen[1] <= height) {
                // Рисуем точку
                g2d.setColor(new Color(255, 165, 0, 200)); // Оранжевый
                g2d.fillOval((int)screen[0] - 4, (int)screen[1] - 4, 8, 8);

                // Рисуем подпись
                g2d.setColor(new Color(0, 0, 0, 200));
                g2d.setFont(new Font("Arial", Font.PLAIN, 10));
                g2d.drawString(data.name, (int)screen[0] + 8, (int)screen[1] - 8);
            }
        }
    }

    private void drawTarget(Graphics2D g2d, int width, int height) {
        double[] screen = geodesy.getGridLinePoint(geodesy.getTargetLat(), geodesy.getTargetLon(), width, height);

        if (screen[0] >= 0 && screen[0] <= width && screen[1] >= 0 && screen[1] <= height) {
            g2d.setColor(new Color(255, 0, 0, 200));
            g2d.setStroke(new BasicStroke(2));

            int size = 10;
            g2d.drawLine((int)screen[0] - size, (int)screen[1], (int)screen[0] + size, (int)screen[1]);
            g2d.drawLine((int)screen[0], (int)screen[1] - size, (int)screen[0], (int)screen[1] + size);
            g2d.drawOval((int)screen[0] - size/2, (int)screen[1] - size/2, size, size);

            g2d.setColor(new Color(0, 0, 0, 200));
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.drawString(geodesy.getTargetName(), (int)screen[0] + 15, (int)screen[1] - 15);

            if (geodesy.isLockedToTarget()) {
                g2d.setColor(new Color(255, 0, 0, 150));
                g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0));
                g2d.drawRect((int)screen[0] - 30, (int)screen[1] - 30, 60, 60);
            }
        }
    }

    private void drawParallel(Graphics2D g2d, double latitude, int width, int height) {
        int segments = 100;
        int[] xPoints = new int[segments + 1];
        int[] yPoints = new int[segments + 1];

        for (int i = 0; i <= segments; i++) {
            double lon = geodesy.getMinLon() + (i * (geodesy.getMaxLon() - geodesy.getMinLon()) / segments);
            double[] pos = geodesy.getGridLinePoint(latitude, lon, width, height);
            xPoints[i] = (int)pos[0];
            yPoints[i] = (int)pos[1];
        }

        g2d.drawPolyline(xPoints, yPoints, segments + 1);
    }

    private void drawMeridian(Graphics2D g2d, double longitude, int width, int height) {
        int segments = 100;
        int[] xPoints = new int[segments + 1];
        int[] yPoints = new int[segments + 1];

        for (int i = 0; i <= segments; i++) {
            double lat = geodesy.getMinLat() + (i * (geodesy.getMaxLat() - geodesy.getMinLat()) / segments);
            double[] pos = geodesy.getGridLinePoint(lat, longitude, width, height);
            xPoints[i] = (int)pos[0];
            yPoints[i] = (int)pos[1];
        }

        g2d.drawPolyline(xPoints, yPoints, segments + 1);
    }

    private void drawTrack(Graphics2D g2d, int width, int height) {
        if (trackPoints.isEmpty()) {
            return;
        }

        g2d.setColor(trackColor);
        g2d.setStroke(new BasicStroke(2));

        TrackPoint prev = null;
        for (TrackPoint point : trackPoints) {
            double[] screen = geodesy.geodeticToScreen(point.latitude, point.longitude, width, height);

            if (screen[0] >= 0 && screen[0] <= width && screen[1] >= 0 && screen[1] <= height) {
                if (prev != null) {
                    double[] prevScreen = geodesy.geodeticToScreen(prev.latitude, prev.longitude, width, height);
                    g2d.drawLine((int)prevScreen[0], (int)prevScreen[1], (int)screen[0], (int)screen[1]);
                }
            } else if (prev != null) {
                double[] prevScreen = geodesy.geodeticToScreen(prev.latitude, prev.longitude, width, height);
                if (prevScreen[0] >= 0 && prevScreen[0] <= width && prevScreen[1] >= 0 && prevScreen[1] <= height) {
                    g2d.drawLine((int)prevScreen[0], (int)prevScreen[1], (int)screen[0], (int)screen[1]);
                }
            }
            prev = point;
        }

        g2d.setColor(Color.BLUE);
        for (TrackPoint point : trackPoints) {
            double[] screen = geodesy.geodeticToScreen(point.latitude, point.longitude, width, height);
            if (screen[0] >= 0 && screen[0] <= width && screen[1] >= 0 && screen[1] <= height) {
                g2d.fillOval((int)screen[0] - 3, (int)screen[1] - 3, 6, 6);
            }
        }

        if (!trackPoints.isEmpty()) {
            TrackPoint last = trackPoints.get(trackPoints.size() - 1);
            double[] screen = geodesy.geodeticToScreen(last.latitude, last.longitude, width, height);

            if (screen[0] >= 0 && screen[0] <= width && screen[1] >= 0 && screen[1] <= height) {
                g2d.setColor(Color.GREEN);
                g2d.fillOval((int)screen[0] - 6, (int)screen[1] - 6, 12, 12);

                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.BOLD, 11));
                String coord = String.format("B=%.4f° L=%.4f° H=%.0fм",
                    Math.toDegrees(last.latitude),
                    Math.toDegrees(last.longitude),
                    last.altitude);
                g2d.drawString(coord, (int)screen[0] + 10, (int)screen[1] - 10);
            }
        }
    }

    private void drawInfo(Graphics2D g2d, int width, int height) {
        g2d.setColor(new Color(255, 255, 255, 180));
        g2d.fillRect(5, 5, 500, 240);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));

        int y = 25;
        g2d.drawString("Источник: " + currentSensor, 10, y);
        g2d.drawString("Система координат: ПЗ-90.02", 10, y + 20);
        g2d.drawString(String.format("Район: с.ш. %.2f-%.2f°, в.д. %.2f-%.2f°",
            geodesy.getMinLat(), geodesy.getMaxLat(), geodesy.getMinLon(), geodesy.getMaxLon()), 10, y + 40);
        g2d.drawString(String.format("Масштаб: %.2fx (мин. 0.01x)", geodesy.getZoomFactor()), 10, y + 60);
        g2d.drawString("Ctrl+колесо - масштаб", 10, y + 80);
        g2d.drawString("Зажать колесо - перемещение", 10, y + 100);

        String lockStatus = geodesy.isLockedToTarget() ? " (привязано)" : "";
        g2d.drawString(String.format("Цель: %s B=%.2f° L=%.2f°%s",
            geodesy.getTargetName(), geodesy.getTargetLat(), geodesy.getTargetLon(), lockStatus), 10, y + 120);

        g2d.drawString("Загружено точек: " + geoDataList.size(), 10, y + 140);

        if (!trackPoints.isEmpty()) {
            TrackPoint last = trackPoints.get(trackPoints.size() - 1);
            g2d.drawString(String.format("Текущие: B=%.4f° L=%.4f° H=%.0fм",
                Math.toDegrees(last.latitude),
                Math.toDegrees(last.longitude),
                last.altitude), 10, y + 160);
            g2d.drawString("Точек в треке: " + trackPoints.size(), 10, y + 180);
        }
    }
}

// Основной класс приложения
public class LoginApp extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private String backgroundImagePath = "background.jpg";
    private TrackPanel trackPanel;
    private SensorDataSource currentDataSource;
    private List<SensorDataSource> availableSensors = new ArrayList<>();
    private JLabel sensorStatusLabel;
    private JLabel zoomStatusLabel;
    private JLabel targetStatusLabel;
    private JLabel fileInfoLabel;

    public LoginApp() {
        setTitle("Авторизация");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 200);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Поле для имени пользователя
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Логин:"), gbc);

        gbc.gridx = 1;
        usernameField = new JTextField(15);
        panel.add(usernameField, gbc);

        // Поле для пароля
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Пароль:"), gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField(15);
        panel.add(passwordField, gbc);

        // Кнопка авторизации
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;

        JButton loginButton = new JButton("Войти");
        loginButton.setPreferredSize(new Dimension(100, 30));
        panel.add(loginButton, gbc);

        // Добавляем обработчик для кнопки
        loginButton.addActionListener(e -> performLogin());

        // Добавляем обработчик для клавиши Enter
        ActionListener enterAction = e -> performLogin();

        // Регистрируем Enter для полей ввода
        usernameField.addActionListener(enterAction);
        passwordField.addActionListener(enterAction);

        // Также добавляем KeyListener для совместимости
        KeyAdapter enterKeyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performLogin();
                }
            }
        };

        usernameField.addKeyListener(enterKeyAdapter);
        passwordField.addKeyListener(enterKeyAdapter);

        // Устанавливаем фокус на поле логина при запуске
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                usernameField.requestFocus();
            }
        });

        add(panel);
    }

    // Метод для выполнения авторизации
    private void performLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        if (username.equals("admin") && password.equals("123")) {
            JOptionPane.showMessageDialog(LoginApp.this,
                "Авторизация успешна!",
                "Успех",
                JOptionPane.INFORMATION_MESSAGE);

            dispose();
            openFullScreenWindow();

        } else {
            JOptionPane.showMessageDialog(LoginApp.this,
                "Неверный логин или пароль",
                "Ошибка",
                JOptionPane.ERROR_MESSAGE);

            // Очищаем поле пароля при ошибке
            passwordField.setText("");
            passwordField.requestFocus(); // Возвращаем фокус в поле пароля
        }
    }

    private void openFullScreenWindow() {
        JFrame fullScreenFrame = new JFrame();
        fullScreenFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fullScreenFrame.setUndecorated(true);

        JPanel mainPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        buttonPanel.setOpaque(false);

        trackPanel = new TrackPanel();

        sensorStatusLabel = new JLabel(" Текущий сенсор: не выбран ");
        sensorStatusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        sensorStatusLabel.setOpaque(true);
        sensorStatusLabel.setBackground(new Color(0, 0, 0, 150));
        sensorStatusLabel.setForeground(Color.WHITE);

        zoomStatusLabel = new JLabel(" Масштаб: 1.00x (Ctrl+колесо) ");
        zoomStatusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        zoomStatusLabel.setOpaque(true);
        zoomStatusLabel.setBackground(new Color(0, 0, 0, 150));
        zoomStatusLabel.setForeground(Color.WHITE);

        targetStatusLabel = new JLabel(" Цель: Аэродром B=55.00° L=37.00° ");
        targetStatusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        targetStatusLabel.setOpaque(true);
        targetStatusLabel.setBackground(new Color(0, 0, 0, 150));
        targetStatusLabel.setForeground(Color.WHITE);

        fileInfoLabel = new JLabel(" Данные не загружены ");
        fileInfoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        fileInfoLabel.setOpaque(true);
        fileInfoLabel.setBackground(new Color(0, 0, 0, 150));
        fileInfoLabel.setForeground(Color.WHITE);

        trackPanel.setZoomLabel(zoomStatusLabel);
        trackPanel.setTargetLabel(targetStatusLabel);
        trackPanel.setFileInfoLabel(fileInfoLabel);

        String[] buttonNames = {
            "Настройки", "Вид", "Сенсоры", "Карта", "Аэродром",
            "Списки", "Метео", "Сообщения", "Загрузка", "Статистика",
            "Архив", "Помощь"
        };

        for (String name : buttonNames) {
            if (name.equals("Настройки")) {
                buttonPanel.add(createSettingsButton(fullScreenFrame));
            } else if (name.equals("Сенсоры")) {
                buttonPanel.add(createSensorsButton(fullScreenFrame));
            } else if (name.equals("Вид")) {
                buttonPanel.add(createViewButton(fullScreenFrame));
            } else if (name.equals("Аэродром")) {
                buttonPanel.add(createAirportButton(fullScreenFrame));
            } else if (name.equals("Карта")) {
                buttonPanel.add(createMapButton(fullScreenFrame));
            } else {
                JButton button = new JButton(name);
                button.setPreferredSize(new Dimension(100, 30));
                button.setFont(new Font("Arial", Font.PLAIN, 12));

                button.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JOptionPane.showMessageDialog(fullScreenFrame,
                            "Нажата кнопка: " + name,
                            "Информация",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                });

                buttonPanel.add(button);
            }
        }

        buttonPanel.add(sensorStatusLabel);
        buttonPanel.add(zoomStatusLabel);
        buttonPanel.add(targetStatusLabel);
        buttonPanel.add(fileInfoLabel);

        initializeSensors();

        mainPanel.add(buttonPanel, BorderLayout.NORTH);

        JPanel backgroundPanel = new JPanel(new BorderLayout()) {
            private Image backgroundImage;

            {
                try {
                    File imageFile = new File(backgroundImagePath);
                    if (imageFile.exists()) {
                        backgroundImage = ImageIO.read(imageFile);
                    }
                } catch (IOException e) {
                    System.err.println("Ошибка загрузки изображения: " + e.getMessage());
                }
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                }
            }
        };

        trackPanel.setOpaque(false);
        backgroundPanel.add(trackPanel, BorderLayout.CENTER);

        mainPanel.add(backgroundPanel, BorderLayout.CENTER);

        fullScreenFrame.add(mainPanel);
        fullScreenFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        mainPanel.setFocusable(true);
        mainPanel.requestFocusInWindow();
        mainPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    stopAllSensors();
                    fullScreenFrame.dispose();
                    System.exit(0);
                }
            }
        });

        fullScreenFrame.setVisible(true);
    }

    private void initializeSensors() {
        availableSensors.add(new RadarDataSource());
        availableSensors.add(new GPSDataSource());
        availableSensors.add(new ADSBSource());

        if (!availableSensors.isEmpty()) {
            selectSensor(availableSensors.get(0));
        }
    }

    private void selectSensor(SensorDataSource sensor) {
        if (currentDataSource != null) {
            currentDataSource.stopSimulation();
        }

        currentDataSource = sensor;

        if (sensorStatusLabel != null) {
            sensorStatusLabel.setText(" Текущий сенсор: " + sensor.getName() + " ");
        }

        Timer updateTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentDataSource != null) {
                    trackPanel.updateTrack(
                        currentDataSource.getTrackData(),
                        currentDataSource.getName()
                    );
                }
            }
        });
        updateTimer.start();
    }

    private void stopAllSensors() {
        for (SensorDataSource sensor : availableSensors) {
            sensor.stopSimulation();
        }
    }

    private JButton createSettingsButton(JFrame parentFrame) {
        JButton settingsButton = new JButton("Настройки");
        settingsButton.setPreferredSize(new Dimension(100, 30));
        settingsButton.setFont(new Font("Arial", Font.PLAIN, 12));

        JPopupMenu settingsMenu = new JPopupMenu();

        JMenuItem authItem = new JMenuItem("Авторизация");
        authItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(parentFrame,
                "Выбрана Авторизация",
                "Настройки",
                JOptionPane.INFORMATION_MESSAGE);
        });
        settingsMenu.add(authItem);

        JMenuItem saveConfigItem = new JMenuItem("Сохранение конфигурации");
        saveConfigItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(parentFrame,
                "Выбрано Сохранение конфигурации",
                "Настройки",
                JOptionPane.INFORMATION_MESSAGE);
        });
        settingsMenu.add(saveConfigItem);

        JMenuItem loadConfigItem = new JMenuItem("Загрузка конфигурации");
        loadConfigItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(parentFrame,
                "Выбрана Загрузка конфигурации",
                "Настройки",
                JOptionPane.INFORMATION_MESSAGE);
        });
        settingsMenu.add(loadConfigItem);

        settingsMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Выход");
        exitItem.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(parentFrame,
                "Вы действительно хотите выйти?",
                "Подтверждение выхода",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                stopAllSensors();
                parentFrame.dispose();
                System.exit(0);
            }
        });
        settingsMenu.add(exitItem);

        settingsButton.addActionListener(e -> {
            settingsMenu.show(settingsButton, 0, settingsButton.getHeight());
        });

        return settingsButton;
    }

    private JButton createSensorsButton(JFrame parentFrame) {
        JButton sensorsButton = new JButton("Сенсоры");
        sensorsButton.setPreferredSize(new Dimension(100, 30));
        sensorsButton.setFont(new Font("Arial", Font.PLAIN, 12));

        JPopupMenu sensorsMenu = new JPopupMenu();

        for (SensorDataSource sensor : availableSensors) {
            JMenuItem sensorItem = new JMenuItem(sensor.getName());
            sensorItem.addActionListener(e -> {
                selectSensor(sensor);
                JOptionPane.showMessageDialog(parentFrame,
                    "Выбран источник данных: " + sensor.getName(),
                    "Сенсоры",
                    JOptionPane.INFORMATION_MESSAGE);
            });
            sensorsMenu.add(sensorItem);
        }

        sensorsMenu.addSeparator();

        JMenuItem stopItem = new JMenuItem("Остановить все сенсоры");
        stopItem.addActionListener(e -> {
            stopAllSensors();
            trackPanel.clearTrack();
            if (sensorStatusLabel != null) {
                sensorStatusLabel.setText(" Текущий сенсор: не выбран ");
            }
            JOptionPane.showMessageDialog(parentFrame,
                "Все сенсоры остановлены",
                "Сенсоры",
                JOptionPane.INFORMATION_MESSAGE);
        });
        sensorsMenu.add(stopItem);

        sensorsButton.addActionListener(e -> {
            sensorsMenu.show(sensorsButton, 0, sensorsButton.getHeight());
        });

        return sensorsButton;
    }

    private JButton createViewButton(JFrame parentFrame) {
        JButton viewButton = new JButton("Вид");
        viewButton.setPreferredSize(new Dimension(100, 30));
        viewButton.setFont(new Font("Arial", Font.PLAIN, 12));

        JPopupMenu viewMenu = new JPopupMenu();

        JCheckBoxMenuItem gridItem = new JCheckBoxMenuItem("Показать сетку", true);
        gridItem.addActionListener(e -> {
            trackPanel.setShowGrid(gridItem.isSelected());
        });
        viewMenu.add(gridItem);

        JCheckBoxMenuItem coordsItem = new JCheckBoxMenuItem("Показать координаты", true);
        coordsItem.addActionListener(e -> {
            trackPanel.setShowCoordinates(coordsItem.isSelected());
        });
        viewMenu.add(coordsItem);

        JCheckBoxMenuItem geoPointsItem = new JCheckBoxMenuItem("Показать точки из файла", true);
        geoPointsItem.addActionListener(e -> {
            trackPanel.setShowGeoPoints(geoPointsItem.isSelected());
        });
        viewMenu.add(geoPointsItem);

        viewMenu.addSeparator();

        JMenuItem resetZoomItem = new JMenuItem("Сбросить масштаб (1.0x)");
        resetZoomItem.addActionListener(e -> {
            trackPanel.resetZoom();
        });
        viewMenu.add(resetZoomItem);

        viewMenu.addSeparator();

        JMenuItem clearTrackItem = new JMenuItem("Очистить трек");
        clearTrackItem.addActionListener(e -> {
            trackPanel.clearTrack();
        });
        viewMenu.add(clearTrackItem);

        JMenuItem clearDataItem = new JMenuItem("Очистить данные из файла");
        clearDataItem.addActionListener(e -> {
            trackPanel.clearGeoData();
            JOptionPane.showMessageDialog(parentFrame,
                "Данные из файла очищены",
                "Вид",
                JOptionPane.INFORMATION_MESSAGE);
        });
        viewMenu.add(clearDataItem);

        viewButton.addActionListener(e -> {
            viewMenu.show(viewButton, 0, viewButton.getHeight());
        });

        return viewButton;
    }

    private JButton createAirportButton(JFrame parentFrame) {
        JButton airportButton = new JButton("Аэродром");
        airportButton.setPreferredSize(new Dimension(100, 30));
        airportButton.setFont(new Font("Arial", Font.PLAIN, 12));

        JPopupMenu airportMenu = new JPopupMenu();

        JMenuItem setFromTrackItem = new JMenuItem("Установить цель по текущей позиции");
        setFromTrackItem.addActionListener(e -> {
            trackPanel.setTargetFromCurrentPosition();
            JOptionPane.showMessageDialog(parentFrame,
                "Цель установлена на текущую позицию самолёта",
                "Аэродром",
                JOptionPane.INFORMATION_MESSAGE);
        });
        airportMenu.add(setFromTrackItem);

        JMenuItem lockItem = new JMenuItem("Привязать карту к цели");
        lockItem.addActionListener(e -> {
            trackPanel.lockToTarget();
            JOptionPane.showMessageDialog(parentFrame,
                "Карта привязана к цели",
                "Аэродром",
                JOptionPane.INFORMATION_MESSAGE);
        });
        airportMenu.add(lockItem);

        JMenuItem unlockItem = new JMenuItem("Отвязать карту");
        unlockItem.addActionListener(e -> {
            trackPanel.unlockFromTarget();
            JOptionPane.showMessageDialog(parentFrame,
                "Карта отвязана от цели",
                "Аэродром",
                JOptionPane.INFORMATION_MESSAGE);
        });
        airportMenu.add(unlockItem);

        airportMenu.addSeparator();

        JMenuItem centerItem = new JMenuItem("Центрировать на цели");
        centerItem.addActionListener(e -> {
            trackPanel.lockToTarget();
            JOptionPane.showMessageDialog(parentFrame,
                "Карта центрирована на цели",
                "Аэродром",
                JOptionPane.INFORMATION_MESSAGE);
        });
        airportMenu.add(centerItem);

        airportButton.addActionListener(e -> {
            airportMenu.show(airportButton, 0, airportButton.getHeight());
        });

        return airportButton;
    }

    private JButton createMapButton(JFrame parentFrame) {
        JButton mapButton = new JButton("Карта");
        mapButton.setPreferredSize(new Dimension(100, 30));
        mapButton.setFont(new Font("Arial", Font.PLAIN, 12));

        JPopupMenu mapMenu = new JPopupMenu();

        JMenuItem loadTxtItem = new JMenuItem("Загрузить координаты из TXT");
        loadTxtItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Текстовые файлы (*.txt)", "txt"));

            int result = fileChooser.showOpenDialog(parentFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                trackPanel.loadGeoDataFromFile(selectedFile);
            }
        });
        mapMenu.add(loadTxtItem);

        JMenuItem loadXmlItem = new JMenuItem("Загрузить координаты из XML");
        loadXmlItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("XML файлы (*.xml)", "xml"));

            int result = fileChooser.showOpenDialog(parentFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                trackPanel.loadGeoDataFromFile(selectedFile);
            }
        });
        mapMenu.add(loadXmlItem);

        mapMenu.addSeparator();

        JMenuItem clearItem = new JMenuItem("Очистить данные");
        clearItem.addActionListener(e -> {
            trackPanel.clearGeoData();
            JOptionPane.showMessageDialog(parentFrame,
                "Данные очищены",
                "Карта",
                JOptionPane.INFORMATION_MESSAGE);
        });
        mapMenu.add(clearItem);

        mapButton.addActionListener(e -> {
            mapMenu.show(mapButton, 0, mapButton.getHeight());
        });

        return mapButton;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LoginApp().setVisible(true);
            }
        });
    }
}
