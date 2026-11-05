import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.*;

/** CatClient 0.1 — single-file Minecraft Java downloader and local offline launcher. Java 17+. */
public class catclient {
    private final JFrame frame = new JFrame("CatClient 0.1 — Minecraft Launcher");
    private final JTextArea log = new JTextArea();
    private final JComboBox<String> versions = new JComboBox<>();
    private final JComboBox<String> versionFilter = new JComboBox<>(new String[]{"All versions", "Releases", "Snapshots", "Beta", "Alpha", "Installed"});
    private final java.util.List<String> availableVersions = new ArrayList<>();
    private final Map<String,String> versionTypes = new HashMap<>();
    private volatile boolean manifestLoaded = false;
    private final JTextField customCommand = new JTextField();
    private final JTextField player = new JTextField("CatPlayer", 14);
    private final JProgressBar progress = new JProgressBar();
    private final Path minecraft = minecraftDirectory();
    private final Path settings = Paths.get(System.getProperty("user.home"), ".catclient0.1.properties");
    private final Properties config = new Properties();
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
    private volatile boolean busy;
    private final DefaultListModel<String> modList = new DefaultListModel<>();
    private final JList<String> modView = new JList<>(modList);
    private final JCheckBox hudEnabled = new JCheckBox("Show launcher HUD", true);
    private final JLabel hudStatus = new JLabel("HUD: Ready | Game: not running");
    private volatile Process runningGame;

    private final JTextField javaExecutable = new JTextField();
    private final JComboBox<String> themeMode = new JComboBox<>(new String[]{"System", "Dark", "Light"});
    private final JComboBox<String> performanceMode = new JComboBox<>(new String[]{"Balanced", "Performance", "Low memory"});
    private final JSpinner memoryGb = new JSpinner(new SpinnerNumberModel(4, 1, 32, 1));
    private JPanel rootPanel;
    private static final Color LIGHT_BG = new Color(241, 245, 249);
    private static final Color LIGHT_PANEL = Color.WHITE;
    private static final Color LIGHT_FG = new Color(30, 41, 59);
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) { System.err.println("CatClient requires a desktop GUI."); return; }
        SwingUtilities.invokeLater(() -> new catclient().show());
    }
    private static Path minecraftDirectory() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support", "minecraft");
        if (os.contains("win")) return Paths.get(System.getenv().getOrDefault("APPDATA", home), ".minecraft");
        return Paths.get(home, ".minecraft");
    }
    private static final Color BG = new Color(20, 25, 32);
    private static final Color PANEL = new Color(34, 42, 51);
    private static final Color FG = new Color(235, 243, 240);
    private static final Color GREEN = new Color(85, 190, 112);
    private static JLabel label(String text) { JLabel l = new JLabel(text); l.setForeground(FG); return l; }
    private static JButton button(String text, Color color) {
        JButton b = new JButton(text); b.setBackground(color); b.setForeground(Color.WHITE);
        b.setOpaque(true); b.setFocusPainted(false); b.setBorder(new EmptyBorder(12, 16, 12, 16));
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13)); return b;
    }
    private static JPanel section() {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(PANEL); p.setBorder(new EmptyBorder(15, 18, 15, 18)); return p;
    }
    private static JPanel line(Component... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 4)); p.setOpaque(false);
        for (Component c : components) p.add(c); return p;
    }
    private void show() {
        try (InputStream in = Files.newInputStream(settings)) { config.load(in); } catch (IOException ignored) { }
        frame.setTitle("CatClient 0.1 | Minecraft Java Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(850, 660));
        JPanel root = new JPanel(new BorderLayout(0, 14));
        rootPanel = root; root.setBackground(BG); root.setBorder(new EmptyBorder(18, 22, 18, 22));
        frame.setContentPane(root);

        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        JLabel title = label("CATCLIENT  0.1");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        header.add(title, BorderLayout.WEST);
        JLabel tagline = label("JAVA EDITION  |  CAT LAUNCHER");
        tagline.setForeground(GREEN); tagline.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        JButton mainMods = button("[ MODS ]", new Color(68, 86, 101));
        mainMods.addActionListener(e -> showModsMenu());
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerActions.setOpaque(false);
        headerActions.add(tagline);
        headerActions.add(mainMods);
        header.add(headerActions, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(); content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JPanel hero = section();
        JLabel welcome = label("Welcome back, CatPlayer!");
        welcome.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
        hero.add(welcome); hero.add(Box.createVerticalStrut(6));
        JLabel description = label("Choose your Minecraft version and launch local single-player.");
        description.setForeground(new Color(174, 191, 192)); hero.add(description);
        content.add(hero); content.add(Box.createVerticalStrut(12));

        JPanel play = section();
        JLabel playTitle = label("PLAY MINECRAFT");
        playTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        play.add(playTitle); play.add(Box.createVerticalStrut(12));
        player.setText(config.getProperty("player", "CatPlayer"));
        play.add(line(label("Player name:"), player));
        JButton refresh = button("Refresh", new Color(68, 86, 101));
        refresh.addActionListener(e -> loadVersionManifest());
        versions.setPreferredSize(new Dimension(300, 29));
        versionFilter.addActionListener(e -> refreshVersions());
        play.add(line(label("Show:"), versionFilter));
        play.add(line(label("Version:"), versions, refresh));
        JButton offline = button("PLAY OFFLINE", GREEN.darker());
        offline.addActionListener(e -> runAsync(() -> launchOffline()));
        JButton download = button("INSTALL VERSION", new Color(55, 105, 154));
        download.addActionListener(e -> runAsync(() -> installSelected()));
        play.add(line(offline, download));
        content.add(play); content.add(Box.createVerticalStrut(12));

        JPanel settingsPanel = section();
        JLabel settingsTitle = label("LAUNCHER SETTINGS");
        settingsTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        settingsPanel.add(settingsTitle); settingsPanel.add(Box.createVerticalStrut(8));
        themeMode.setSelectedItem(config.getProperty("theme", "System"));
        themeMode.addActionListener(e -> applyTheme());
        performanceMode.setSelectedItem(config.getProperty("performance", "Balanced"));
        try { memoryGb.setValue(Integer.parseInt(config.getProperty("memoryGb", "4"))); } catch (Exception ignored) { memoryGb.setValue(4); }
        settingsPanel.add(line(label("Appearance:"), themeMode, label("FPS preset:"), performanceMode, label("RAM (GB):"), memoryGb));
        settingsPanel.add(label("Performance settings tune Java; Sodium/Lithium/FerriteCore need a compatible Fabric installation."));
        settingsPanel.add(Box.createVerticalStrut(8));
        settingsPanel.add(label("Minecraft Java executable (leave blank for automatic detection):"));
        javaExecutable.setText(config.getProperty("java", ""));
        javaExecutable.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        settingsPanel.add(javaExecutable); settingsPanel.add(Box.createVerticalStrut(8));
        settingsPanel.add(label("Official launcher executable (optional):"));
        customCommand.setText(config.getProperty("command", ""));
        customCommand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        settingsPanel.add(customCommand); settingsPanel.add(Box.createVerticalStrut(8));
        JButton official = button("OFFICIAL LAUNCHER", new Color(68, 86, 101));
        official.addActionListener(e -> runAsync(() -> launchOfficial()));
        JButton mods = button("MODS", new Color(68, 86, 101));
        mods.addActionListener(e -> showModsMenu());
        JButton folder = button("GAME FOLDER", new Color(68, 86, 101));
        folder.addActionListener(e -> openFolder(minecraft));
        settingsPanel.add(line(official, mods, folder));
        settingsPanel.add(Box.createVerticalStrut(8));
        settingsPanel.add(label("INSTALLED MOD FILES (Fabric/Forge/NeoForge required in-game)"));
        modView.setVisibleRowCount(4);
        JScrollPane modScroll = new JScrollPane(modView);
        modScroll.setPreferredSize(new Dimension(580, 85));
        settingsPanel.add(modScroll);
        JButton rescanMods = button("RESCAN MODS", new Color(68, 86, 101));
        rescanMods.addActionListener(e -> scanMods());
        settingsPanel.add(line(rescanMods, label("Listing files does not load mods into vanilla Minecraft.")));
        hudEnabled.setSelected(Boolean.parseBoolean(config.getProperty("hud", "true")));
        hudEnabled.addActionListener(e -> { hudStatus.setVisible(hudEnabled.isSelected()); save(); });
        settingsPanel.add(line(hudEnabled));
        hudStatus.setVisible(hudEnabled.isSelected());
        settingsPanel.add(hudStatus);
        new javax.swing.Timer(1000, e -> updateHud()).start();
        scanMods();
        content.add(settingsPanel);
        root.add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8)); footer.setOpaque(false);
        progress.setStringPainted(true); progress.setString("Ready");
        footer.add(progress, BorderLayout.NORTH);
        log.setEditable(false); log.setRows(7);
        log.setBackground(new Color(13, 18, 23)); log.setForeground(new Color(178, 229, 184));
        log.setCaretColor(Color.WHITE); log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        footer.add(new JScrollPane(log), BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        refreshVersions();
        loadVersionManifest();
        write("CatClient ready. Installs official Mojang game files.");
        write("Offline mode is for local single-player; online authentication is not provided.");
        write("Game folder: " + minecraft);
        frame.pack(); frame.setLocationRelativeTo(null); applyTheme(); frame.setVisible(true);
    }
    private void showModsMenu() {
        scanMods();
        JDialog dialog = new JDialog(frame, "CatClient | Mods", false);
        dialog.setSize(620, 430);
        dialog.setLocationRelativeTo(frame);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        JLabel heading = new JLabel("[ MODS ]  Installed mod files");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 19f));
        panel.add(heading, BorderLayout.NORTH);
        JList<String> list = new JList<>(modList);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton importButton = new JButton("Add .jar mods");
        importButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Minecraft mod JARs", "jar"));
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    Path destination = minecraft.resolve("mods");
                    Files.createDirectories(destination);
                    for (File file : chooser.getSelectedFiles()) {
                        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                        Path target = destination.resolve(file.getName());
                        if (Files.exists(target)) {
                            int answer = JOptionPane.showConfirmDialog(dialog, "Replace " + file.getName() + "?", "Existing mod", JOptionPane.YES_NO_OPTION);
                            if (answer != JOptionPane.YES_OPTION) continue;
                        }
                        Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    scanMods();
                } catch (IOException ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Mod import failed", JOptionPane.ERROR_MESSAGE); }
            }
        });
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> scanMods());
        JButton folderButton = new JButton("Open mods folder");
        folderButton.addActionListener(e -> openFolder(minecraft.resolve("mods")));
        actions.add(importButton); actions.add(refreshButton); actions.add(folderButton);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(new JLabel("Mods require a compatible Fabric, Forge, or NeoForge game profile."), BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }
    private void scanMods() {
        modList.clear();
        Path mods = minecraft.resolve("mods");
        try {
            Files.createDirectories(mods);
            try (java.util.stream.Stream<Path> files = Files.list(mods)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted().forEach(p -> modList.addElement(p.getFileName().toString()));
            }
            if (modList.isEmpty()) modList.addElement("No .jar mods found in " + mods);
        } catch (IOException ex) { modList.addElement("Unable to read mods: " + ex.getMessage()); }
    }
    private void updateHud() {
        Process p = runningGame;
        String state = p == null ? "not started" : p.isAlive() ? "running (PID " + p.pid() + ")" : "stopped";
        hudStatus.setText("HUD: " + chosen() + " | Minecraft: " + state + " | Mods: " +
            (modList.size() == 1 && modList.get(0).startsWith("No .jar") ? 0 : modList.size()) + " files");
    }
    private boolean darkTheme() {
        String mode = String.valueOf(themeMode.getSelectedItem());
        if ("Dark".equals(mode)) return true;
        if ("Light".equals(mode)) return false;
        String apple = System.getProperty("apple.awt.application.appearance", "");
        String desktop = String.valueOf(Toolkit.getDefaultToolkit().getDesktopProperty("apple.awt.application.appearance"));
        return (apple + desktop).toLowerCase(Locale.ROOT).contains("dark") ||
               (osName().equals("osx") && macDarkMode());
    }
    private boolean macDarkMode() {
        try {
            Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
            String output = new String(p.getInputStream().readAllBytes());
            return p.waitFor() == 0 && output.trim().equalsIgnoreCase("Dark");
        } catch (Exception ignored) { return false; }
    }
    private void applyTheme() {
        boolean dark = darkTheme();
        Color bg = dark ? BG : LIGHT_BG, panel = dark ? PANEL : LIGHT_PANEL;
        Color fg = dark ? FG : LIGHT_FG;
        UIManager.put("Panel.background", panel);
        UIManager.put("Label.foreground", fg);
        UIManager.put("TextField.background", dark ? new Color(45,54,65) : Color.WHITE);
        UIManager.put("TextField.foreground", fg);
        UIManager.put("ComboBox.background", panel);
        UIManager.put("ComboBox.foreground", fg);
        UIManager.put("Spinner.background", panel);
        UIManager.put("Spinner.foreground", fg);
        SwingUtilities.updateComponentTreeUI(frame);
        recolor(rootPanel, bg, panel, fg);
        rootPanel.setBackground(bg);
        log.setBackground(dark ? new Color(13,18,23) : new Color(231,238,244));
        log.setForeground(dark ? new Color(178,229,184) : new Color(24,85,50));
        frame.repaint();
    }
    private void recolor(Component c, Color bg, Color panel, Color fg) {
        if (c instanceof JLabel) {
            JLabel l = (JLabel)c;
            if (!l.getText().contains("JAVA EDITION")) l.setForeground(fg);
        }
        if (c instanceof JPanel) {
            JPanel p = (JPanel)c;
            if (p == rootPanel) p.setBackground(bg);
            else if (p.isOpaque()) p.setBackground(panel);
        }
        if (c instanceof JTextField) { c.setBackground(panel); c.setForeground(fg); }
        if (c instanceof JComboBox || c instanceof JSpinner) { c.setBackground(panel); c.setForeground(fg); }
        if (c instanceof Container) for (Component child : ((Container)c).getComponents()) recolor(child,bg,panel,fg);
    }
    private void write(String s) { SwingUtilities.invokeLater(() -> { log.append(s + "\n"); log.setCaretPosition(log.getDocument().getLength()); }); }
    private void status(String s) { SwingUtilities.invokeLater(() -> progress.setString(s)); }
    private void runAsync(ThrowingRunnable task) {
        if (busy) { write("Another operation is running."); return; }
        busy = true; progress.setIndeterminate(true);
        new Thread(() -> { try { task.run(); } catch (Exception e) { write("ERROR: " + e); for (StackTraceElement t : e.getStackTrace()) write("  at " + t); }
            finally { busy = false; SwingUtilities.invokeLater(() -> { progress.setIndeterminate(false); progress.setString("Ready"); refreshVersions(); }); }
        }, "CatClient-worker").start();
    }
    interface ThrowingRunnable { void run() throws Exception; }
    private void loadVersionManifest() {
        status("Loading Minecraft version catalog...");
        new Thread(() -> {
            try {
                Map<String,Object> manifest = obj(Json.parse(fetch(MANIFEST)));
                java.util.List<String> ids = new ArrayList<>();
                Map<String,String> types = new HashMap<>();
                for (Object item : arr(manifest.get("versions"))) {
                    Map<String,Object> entry = obj(item);
                    String id = str(entry.get("id"));
                    if (id.isEmpty()) continue;
                    ids.add(id);
                    types.put(id, str(entry.get("type")));
                }
                SwingUtilities.invokeLater(() -> {
                    availableVersions.clear(); availableVersions.addAll(ids);
                    versionTypes.clear(); versionTypes.putAll(types);
                    manifestLoaded = true;
                    refreshVersions();
                    status("Loaded " + ids.size() + " Minecraft versions");
                    write("Version catalog loaded: " + ids.size() + " versions (releases, snapshots, beta, alpha).");
                });
            } catch (Exception e) {
                write("Could not load online version catalog: " + e.getMessage());
                write("Installed versions are still available. Click Refresh to retry.");
                status("Offline catalog");
            }
        }, "CatClient-version-catalog").start();
    }
    private void refreshVersions() {
        Object prior = versions.getSelectedItem();
        String filter = String.valueOf(versionFilter.getSelectedItem());
        LinkedHashSet<String> installed = new LinkedHashSet<>();
        Path dir = minecraft.resolve("versions");
        if (Files.isDirectory(dir)) try (java.util.stream.Stream<Path> paths = Files.list(dir)) {
            paths.filter(Files::isDirectory).forEach(p -> installed.add(p.getFileName().toString()));
        } catch (IOException e) { write("Cannot read versions: " + e.getMessage()); }
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        if (!"Installed".equals(filter)) {
            if ("All versions".equals(filter) || "Releases".equals(filter)) choices.add("latest-release");
            if (manifestLoaded) for (String id : availableVersions) {
                String type = versionTypes.getOrDefault(id, "");
                if ("All versions".equals(filter) ||
                    ("Releases".equals(filter) && "release".equals(type)) ||
                    ("Snapshots".equals(filter) && "snapshot".equals(type)) ||
                    ("Beta".equals(filter) && "old_beta".equals(type)) ||
                    ("Alpha".equals(filter) && "old_alpha".equals(type))) choices.add(id);
            }
        }
        if ("Installed".equals(filter) || "All versions".equals(filter)) {
            installed.stream().sorted().forEach(choices::add);
        }
        versions.removeAllItems();
        for (String id : choices) versions.addItem(id);
        if (prior != null && choices.contains(prior.toString())) versions.setSelectedItem(prior);
        else if (choices.contains("latest-release")) versions.setSelectedItem("latest-release");
        else if (versions.getItemCount() > 0) versions.setSelectedIndex(0);
    }
    private String chosen() { Object v = versions.getSelectedItem(); return v == null ? "latest-release" : v.toString().trim(); }
    private void save() {
        config.setProperty("hud", Boolean.toString(hudEnabled.isSelected()));
        config.setProperty("java", javaExecutable.getText().trim());
        config.setProperty("theme", String.valueOf(themeMode.getSelectedItem()));
        config.setProperty("performance", String.valueOf(performanceMode.getSelectedItem()));
        config.setProperty("memoryGb", String.valueOf(memoryGb.getValue())); config.setProperty("command", customCommand.getText().trim()); config.setProperty("player", player.getText().trim());
        try (OutputStream out = Files.newOutputStream(settings)) { config.store(out, "CatClient settings"); }
        catch (IOException e) { write("Settings: " + e.getMessage()); }
    }
    private byte[] fetch(String url) throws Exception {
        if (!url.startsWith("https://")) throw new IOException("Refusing non-HTTPS URL: " + url);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).GET().build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + " downloading " + url);
        return res.body();
    }
    private static String sha1(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
        StringBuilder b = new StringBuilder(); for (byte x : digest) b.append(String.format("%02x", x & 255)); return b.toString();
    }
    private void download(String url, Path target, String expectedSha1) throws Exception {
        if (Files.exists(target) && (expectedSha1 == null || sha1(Files.readAllBytes(target)).equalsIgnoreCase(expectedSha1))) return;
        byte[] data = fetch(url);
        if (expectedSha1 != null && !sha1(data).equalsIgnoreCase(expectedSha1)) throw new IOException("Checksum mismatch: " + target);
        Files.createDirectories(target.getParent()); Path temp = target.resolveSibling(target.getFileName() + ".part");
        try { Files.write(temp, data); Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    private Map<String,Object> versionMeta(String id) throws Exception {
        Map<String,Object> manifest = obj(Json.parse(fetch(MANIFEST)));
        if (id.equals("latest-release")) id = str(obj(manifest.get("latest")).get("release"));
        for (Object item : arr(manifest.get("versions"))) {
            Map<String,Object> v = obj(item);
            if (id.equals(str(v.get("id")))) {
                Path path = minecraft.resolve("versions").resolve(id).resolve(id + ".json");
                download(str(v.get("url")), path, str(v.get("sha1")));
                return obj(Json.parse(Files.readAllBytes(path)));
            }
        }
        Path local = minecraft.resolve("versions").resolve(id).resolve(id + ".json");
        if (Files.exists(local)) return obj(Json.parse(Files.readAllBytes(local)));
        throw new IOException("Unknown Minecraft version: " + id);
    }
    private void installSelected() throws Exception {
        String id = chosen(); status("Fetching version metadata..."); write("Installing " + id + "...");
        Map<String,Object> meta = versionMeta(id); id = str(meta.get("id"));
        Map<String,Object> client = obj(obj(meta.get("downloads")).get("client"));
        download(str(client.get("url")), minecraft.resolve("versions").resolve(id).resolve(id + ".jar"), str(client.get("sha1")));
        List<String> cp = new ArrayList<>(); int count = 0;
        for (Object item : arr(meta.get("libraries"))) {
            Map<String,Object> lib = obj(item); if (!allowed(lib)) continue;
            Map<String,Object> downloads = objOrEmpty(lib.get("downloads"));
            Map<String,Object> artifact = objOrEmpty(downloads.get("artifact"));
            if (!artifact.isEmpty()) {
                String rel = str(artifact.get("path")); Path file = minecraft.resolve("libraries").resolve(rel);
                download(str(artifact.get("url")), file, str(artifact.get("sha1"))); cp.add(file.toString()); count++;
            }
            Map<String,Object> classifiers = objOrEmpty(downloads.get("classifiers"));
            String nativeKey = nativeKey(lib);
            if (nativeKey == null && lib.get("name") != null && str(lib.get("name")).contains("lwjgl")) {
                String platform = osName().equals("osx") ? "natives-macos" : osName().equals("windows") ? "natives-windows" : "natives-linux";
                if (System.getProperty("os.arch", "").matches("aarch64|arm64")) {
                    if (classifiers.containsKey(platform + "-arm64")) platform += "-arm64";
                    else if (classifiers.containsKey(platform + "-aarch64")) platform += "-aarch64";
                }
                if (classifiers.containsKey(platform)) nativeKey = platform;
            }
            if (nativeKey != null && classifiers.containsKey(nativeKey)) {
                Map<String,Object> nativeArt = obj(classifiers.get(nativeKey));
                Path archive = minecraft.resolve("libraries").resolve(str(nativeArt.get("path")));
                download(str(nativeArt.get("url")), archive, str(nativeArt.get("sha1")));
                extractNatives(archive, minecraft.resolve("versions").resolve(id).resolve("natives"));
            }
            if (count % 15 == 0) status("Libraries: " + count);
        }
        Map<String,Object> assetIndex = objOrEmpty(meta.get("assetIndex"));
        if (!assetIndex.isEmpty()) {
            String indexId = str(assetIndex.get("id"));
            Path index = minecraft.resolve("assets").resolve("indexes").resolve(indexId + ".json");
            download(str(assetIndex.get("url")), index, str(assetIndex.get("sha1")));
            Map<String,Object> objects = obj(obj(Json.parse(Files.readAllBytes(index))).get("objects"));
            int n = 0;
            for (Object value : objects.values()) {
                String hash = str(obj(value).get("hash"));
                Path file = minecraft.resolve("assets").resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                download("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash, file, hash);
                if (++n % 100 == 0) status("Assets: " + n + "/" + objects.size());
            }
            write("Downloaded " + n + " assets.");
        }
        write("Installed " + id + " (" + count + " Java libraries). Ready for offline single-player.");
    }
    private void launchOffline() throws Exception {
        save(); String id = chosen();
        if (id.equals("latest-release")) { installSelected(); id = str(versionMeta(id).get("id")); }
        Path json = minecraft.resolve("versions").resolve(id).resolve(id + ".json");
        Path jar = minecraft.resolve("versions").resolve(id).resolve(id + ".jar");
        if (!Files.exists(json) || !Files.exists(jar)) throw new IOException("Install this version first: " + id);
        Map<String,Object> meta = obj(Json.parse(Files.readAllBytes(json)));
        if (meta.containsKey("inheritsFrom")) throw new IOException("This modded profile inherits from another version; CatClient cannot yet resolve its parent libraries. Use a supported modded launcher for this profile.");
        if (Files.isDirectory(minecraft.resolve("mods"))) {
            try (java.util.stream.Stream<Path> modFiles = Files.list(minecraft.resolve("mods"))) {
                if (modFiles.anyMatch(p -> p.getFileName().toString().endsWith(".jar")))
                    write("NOTICE: Mod JARs are present, but vanilla Minecraft will NOT load them. Select a correctly installed Fabric/Forge/NeoForge profile.");
            }
        }
        String username = player.getText().trim();
        if (!username.matches("[A-Za-z0-9_]{3,16}")) throw new IOException("Offline name must be 3–16 letters, numbers or underscores.");
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
        String os = osName(); String arch = System.getProperty("os.arch", "");
        int requiredJava = 8;
        Map<String,Object> javaMeta = objOrEmpty(meta.get("javaVersion"));
        if (javaMeta.get("majorVersion") instanceof Number) requiredJava = ((Number)javaMeta.get("majorVersion")).intValue();
        String javaBin = selectJava(requiredJava);
        write("Minecraft requires Java " + requiredJava + "; selected " + javaBin);
        List<String> cp = new ArrayList<>();
        for (Object item : arr(meta.get("libraries"))) {
            Map<String,Object> lib = obj(item); if (!allowed(lib)) continue;
            Map<String,Object> artifact = objOrEmpty(objOrEmpty(lib.get("downloads")).get("artifact"));
            if (!artifact.isEmpty()) { Path file = minecraft.resolve("libraries").resolve(str(artifact.get("path")));
                if (!Files.exists(file)) throw new IOException("Missing library; reinstall: " + file.getFileName()); cp.add(file.toString()); }
        }
        cp.add(jar.toString()); Path natives = minecraft.resolve("versions").resolve(id).resolve("natives");
        Files.createDirectories(natives);
        Map<String,String> vars = new HashMap<>();
        vars.put("${auth_player_name}", username); vars.put("${version_name}", id);
        vars.put("${game_directory}", minecraft.toString()); vars.put("${assets_root}", minecraft.resolve("assets").toString());
        vars.put("${assets_index_name}", str(meta.getOrDefault("assets", id)));
        vars.put("${auth_uuid}", uuid); vars.put("${auth_access_token}", "0"); vars.put("${user_type}", "legacy");
        vars.put("${version_type}", str(meta.getOrDefault("type", "release")));
        vars.put("${user_properties}", "{}"); vars.put("${auth_session}", "offline");
        vars.put("${classpath}", String.join(File.pathSeparator, cp)); vars.put("${classpath_separator}", File.pathSeparator);
        vars.put("${natives_directory}", natives.toString()); vars.put("${library_directory}", minecraft.resolve("libraries").toString()); vars.put("${primary_jar}", jar.toString()); vars.put("${arch}", System.getProperty("os.arch", "")); vars.put("${log_config_path}", ""); vars.put("${launcher_name}", "CatClient"); vars.put("${launcher_version}", "0.1");
        vars.put("${clientid}", ""); vars.put("${auth_xuid}", "");
        vars.put("${quickPlayPath}", ""); vars.put("${quickPlaySingleplayer}", ""); vars.put("${quickPlayMultiplayer}", ""); vars.put("${quickPlayRealms}", "");
        vars.put("${resolution_width}", "854"); vars.put("${resolution_height}", "480");
        List<String> cmd = new ArrayList<>(); cmd.add(javaBin);
        int ram = (Integer) memoryGb.getValue();
        cmd.add("-Xmx" + ram + "G");
        String preset = String.valueOf(performanceMode.getSelectedItem());
        if ("Performance".equals(preset)) {
            cmd.add("-XX:+UseG1GC");
            cmd.add("-XX:MaxGCPauseMillis=80");
            cmd.add("-XX:+ParallelRefProcEnabled");
        } else if ("Low memory".equals(preset)) {
            cmd.add("-XX:+UseG1GC");
            cmd.add("-XX:MaxGCPauseMillis=150");
        }
        if (os.equals("osx")) cmd.add("-XstartOnFirstThread");
        Map<String,Object> arguments = objOrEmpty(meta.get("arguments"));
        if (arguments.containsKey("jvm")) addArguments(cmd, arr(arguments.get("jvm")), vars);
        if (os.equals("osx")) {
            // The launcher supplies this once; duplicate JVM flags are unnecessary.
            boolean seen = false;
            for (int i = cmd.size()-1; i >= 0; i--) if (cmd.get(i).equals("-XstartOnFirstThread")) {
                if (seen) cmd.remove(i); else seen = true;
            }
        }
        // Some profiles omit these JVM arguments; supply them if missing.
        if (cmd.stream().noneMatch(x -> x.startsWith("-Djava.library.path="))) cmd.add("-Djava.library.path=" + natives);
        if (!cmd.contains("-cp") && cmd.stream().noneMatch(x -> x.equals(vars.get("${classpath}")))) { cmd.add("-cp"); cmd.add(vars.get("${classpath}")); }
        cmd.add(str(meta.get("mainClass")));
        if (arguments.containsKey("game")) addArguments(cmd, arr(arguments.get("game")), vars);
        else if (meta.containsKey("minecraftArguments")) for (String part : str(meta.get("minecraftArguments")).split("\\s+")) cmd.add(replace(part, vars));
        write("Starting " + id + " as " + username + " (offline mode)...");
        Process process = new ProcessBuilder(cmd).directory(minecraft.toFile()).redirectErrorStream(true).start();
        runningGame = process;
        new Thread(() -> { try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line; while ((line = r.readLine()) != null) write("[Minecraft] " + line);
        } catch (IOException e) { write("Game output: " + e.getMessage()); }
        }, "CatClient-game-log").start();
        write("Game process started (PID " + process.pid() + "). Online authentication is not available.");
        new Thread(() -> { try { int exit = process.waitFor();
            if (exit == 0) write("Minecraft exited normally.");
            else { write("MINECRAFT CRASHED: exit code " + exit + ". Check the [Minecraft] lines above and crash-reports folder.");
                write("Crash reports: " + minecraft.resolve("crash-reports"));
                write("Java: " + javaBin + " | required major version: " + requiredJavaFinal(meta)); }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }, "CatClient-game-wait").start();
    }
    private void addArguments(List<String> out, List<Object> args, Map<String,String> vars) {
        for (Object arg : args) {
            if (arg instanceof String) out.add(replace((String)arg, vars));
            else if (arg instanceof Map) {
                Map<String,Object> a = obj(arg); if (!allowed(a)) continue;
                Object value = a.get("value"); if (value instanceof String) out.add(replace((String)value, vars));
                else if (value instanceof List) for (Object v : arr(value)) out.add(replace(str(v), vars));
            }
        }
    }
    private static String replace(String s, Map<String,String> vars) {
        for (Map.Entry<String,String> e : vars.entrySet()) s = s.replace(e.getKey(), e.getValue()); return s;
    }
    private static String osName() { String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") ? "osx" : os.contains("win") ? "windows" : "linux"; }
    private static boolean allowed(Map<String,Object> lib) {
        List<Object> rules = arrOrEmpty(lib.get("rules")); if (rules.isEmpty()) return true;
        boolean permitted = false;
        for (Object rule : rules) {
            Map<String,Object> r = obj(rule); Map<String,Object> os = objOrEmpty(r.get("os"));
            if (!os.isEmpty()) {
                if (os.containsKey("name") && !osName().equals(str(os.get("name")))) continue;
                if (os.containsKey("arch") && !System.getProperty("os.arch", "").matches(str(os.get("arch")))) continue;
                if (os.containsKey("version") && !System.getProperty("os.version", "").matches(str(os.get("version")))) continue;
            }
            if (r.containsKey("features")) {
                Map<String,Object> features = objOrEmpty(r.get("features"));
                boolean match = true;
                for (Map.Entry<String,Object> feature : features.entrySet()) {
                    // Offline launcher has no custom-resolution, demo or quick-play feature enabled.
                    if (!Boolean.FALSE.equals(feature.getValue())) { match = false; break; }
                }
                if (!match) continue;
            }
            permitted = "allow".equals(str(r.get("action")));
        }
        return permitted;
    }
    private static String nativeKey(Map<String,Object> lib) {
        Map<String,Object> natives = objOrEmpty(lib.get("natives")); String key = str(natives.get(osName()));
        if (key.isEmpty()) return null;
        String arch = System.getProperty("os.arch", "").contains("64") || System.getProperty("os.arch", "").equals("aarch64") ? "64" : "32";
        return key.replace("${arch}", arch);
    }
    private static void extractNatives(Path archive, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry; while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName(); if (entry.isDirectory() || name.startsWith("META-INF/") || name.startsWith("META-INF\\")) continue;
                Path dest = target.resolve(name).normalize(); if (!dest.startsWith(target)) throw new IOException("Unsafe native archive entry");
                Files.createDirectories(dest.getParent()); Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
    private static int requiredJavaFinal(Map<String,Object> meta) {
        Object n = objOrEmpty(meta.get("javaVersion")).get("majorVersion");
        return n instanceof Number ? ((Number)n).intValue() : 8;
    }
    private String selectJava(int required) throws Exception {
        String found = findJava(required);
        if (found != null) return found;
        if (osName().equals("osx")) {
            Path brew = Files.isExecutable(Paths.get("/opt/homebrew/bin/brew"))
                ? Paths.get("/opt/homebrew/bin/brew") : Paths.get("/usr/local/bin/brew");
            if (Files.isExecutable(brew)) {
                final int[] answer = new int[1];
                SwingUtilities.invokeAndWait(() -> answer[0] = JOptionPane.showConfirmDialog(frame,
                    "Minecraft requires Java " + required + ", but no compatible runtime was found.\n" +
                    "Install OpenJDK " + required + " with Homebrew now?\n" +
                    "This may take several minutes and requires an internet connection.",
                    "CatClient — Java runtime required", JOptionPane.YES_NO_OPTION));
                if (answer[0] == JOptionPane.YES_OPTION) {
                    status("Installing Java " + required + "...");
                    write("Installing OpenJDK " + required + " using Homebrew...");
                    Process p = new ProcessBuilder(brew.toString(), "install", "openjdk@" + required)
                        .redirectErrorStream(true).start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) write("[Homebrew] " + line);
                    }
                    int exit = p.waitFor();
                    if (exit != 0) write("Homebrew exited with code " + exit);
                    found = findJava(required);
                    if (found != null) return found;
                }
            }
        }
        throw new IOException("No compatible Java runtime found (Minecraft requires Java " + required +
            "). On macOS install with: brew install openjdk@" + required +
            " ; then retry Play Offline. You can also enter a Java executable path in CatClient.");
    }
    private String findJava(int required) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String configured = javaExecutable.getText().trim();
        if (!configured.isEmpty() && Files.isDirectory(Paths.get(configured)))
            configured = Paths.get(configured, "bin", "java").toString();
        if (!configured.isEmpty()) candidates.add(configured);
        if (osName().equals("osx")) {
            for (String prefix : List.of("/opt/homebrew/opt", "/usr/local/opt")) {
                for (int version = Math.max(required, 8); version <= Math.max(required, 30); version++) {
                    candidates.add(prefix + "/openjdk@" + version + "/bin/java");
                    candidates.add(prefix + "/openjdk@" + version + "/libexec/openjdk.jdk/Contents/Home/bin/java");
                }
                candidates.add(prefix + "/openjdk/bin/java");
                candidates.add(prefix + "/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java");
            }
            for (String root : List.of("/Library/Java/JavaVirtualMachines",
                    System.getProperty("user.home") + "/Library/Java/JavaVirtualMachines")) {
                Path jvms = Paths.get(root);
                if (Files.isDirectory(jvms)) try (java.util.stream.Stream<Path> stream = Files.list(jvms)) {
                    stream.map(p -> p.resolve("Contents/Home/bin/java"))
                        .filter(Files::isExecutable).forEach(p -> candidates.add(p.toString()));
                } catch (IOException ignored) { }
            }
            try {
                Process p = new ProcessBuilder("/usr/libexec/java_home", "-v", String.valueOf(required)).start();
                String home = new String(p.getInputStream().readAllBytes()).trim();
                if (p.waitFor() == 0 && !home.isEmpty()) candidates.add(Paths.get(home,"bin","java").toString());
            } catch (Exception ignored) { }
        }
        candidates.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        candidates.add("java");
        for (String c : candidates) {
            if (c.contains("/") && !Files.isExecutable(Paths.get(c))) continue;
            try {
                Process p = new ProcessBuilder(c, "-version").redirectErrorStream(true).start();
                String output = new String(p.getInputStream().readAllBytes());
                if (p.waitFor() != 0) continue;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?").matcher(output);
                if (!m.find()) continue;
                int major = Integer.parseInt(m.group(1));
                if (major == 1 && m.group(2) != null) major = Integer.parseInt(m.group(2));
                if (major >= required && (required >= 17 || major <= 17)) return c;
                write("Skipping Java " + major + " (Minecraft requires " + required + "): " + c);
            } catch (Exception ignored) { }
        }
        return null;
    }
    private void launchOfficial() throws Exception {
        save(); String command = customCommand.getText().trim(); List<List<String>> candidates = new ArrayList<>();
        if (!command.isEmpty()) candidates.add(List.of(command));
        else if (osName().equals("osx")) {
            candidates.add(List.of("open", "-a", "Minecraft Launcher")); candidates.add(List.of("open", "-a", "Minecraft"));
            candidates.add(List.of("open", "/Applications/Minecraft.app"));
        } else if (osName().equals("windows")) candidates.add(List.of("cmd", "/c", "start", "", "minecraft:"));
        else candidates.add(List.of("minecraft-launcher"));
        for (List<String> cmd : candidates) try {
            write("Trying: " + String.join(" ", cmd)); Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (cmd.get(0).equals("open")) {
                String output = new String(p.getInputStream().readAllBytes()); int exit = p.waitFor();
                if (exit != 0) { write("Launch failed: " + output.trim()); continue; }
            }
            write("Official launcher requested."); return;
        } catch (Exception e) { write("Launch failed: " + e.getMessage()); }
        write("Official launcher not found. You can still download and try local offline single-player.");
    }
    private void openFolder(Path folder) {
        try { Files.createDirectories(folder);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) Desktop.getDesktop().open(folder.toFile());
            else write("Folder: " + folder);
        } catch (Exception e) { write("Cannot open folder: " + e.getMessage()); }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> obj(Object o) { return (Map<String,Object>)o; }
    private static Map<String,Object> objOrEmpty(Object o) { return o instanceof Map ? obj(o) : Collections.emptyMap(); }
    @SuppressWarnings("unchecked") private static List<Object> arr(Object o) { return (List<Object>)o; }
    private static List<Object> arrOrEmpty(Object o) { return o instanceof List ? arr(o) : Collections.emptyList(); }
    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    /** Small JSON parser: avoids third-party dependencies in a single .java file. */
    private static class Json {
        private final String s; private int p;
        private Json(String s) { this.s = s; }
        static Object parse(byte[] b) { return new Json(new String(b, java.nio.charset.StandardCharsets.UTF_8)).value(); }
        private void ws() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
        private char next() { return s.charAt(p++); }
        private Object value() {
            ws(); if (p >= s.length()) throw new IllegalArgumentException("Unexpected JSON end"); char c = s.charAt(p);
            if (c == '"') return string(); if (c == '{') return object(); if (c == '[') return array();
            if (s.startsWith("true", p)) { p += 4; return true; } if (s.startsWith("false", p)) { p += 5; return false; }
            if (s.startsWith("null", p)) { p += 4; return null; }
            int start = p; while (p < s.length() && ",}] \r\n\t".indexOf(s.charAt(p)) < 0) p++;
            return Double.valueOf(s.substring(start, p));
        }
        private String string() {
            if (next() != '"') throw new IllegalArgumentException("Expected string"); StringBuilder b = new StringBuilder();
            while (p < s.length()) { char c = next(); if (c == '"') return b.toString();
                if (c == '\\') { char e = next(); switch (e) {
                    case '"': b.append('"'); break; case '\\': b.append('\\'); break; case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break; case 'f': b.append('\f'); break; case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break; case 't': b.append('\t'); break;
                    case 'u': b.append((char)Integer.parseInt(s.substring(p, p+4),16)); p += 4; break;
                    default: throw new IllegalArgumentException("Bad JSON escape"); }
                } else b.append(c);
            } throw new IllegalArgumentException("Unterminated string");
        }
        private Map<String,Object> object() {
            next(); Map<String,Object> m = new LinkedHashMap<>(); ws(); if (s.charAt(p) == '}') { p++; return m; }
            do { ws(); String key = string(); ws(); if (next() != ':') throw new IllegalArgumentException("Expected colon");
                m.put(key, value()); ws(); char c = next(); if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("Expected comma");
            } while (true);
        }
        private List<Object> array() {
            next(); List<Object> a = new ArrayList<>(); ws(); if (s.charAt(p) == ']') { p++; return a; }
            do { a.add(value()); ws(); char c = next(); if (c == ']') return a;
                if (c != ',') throw new IllegalArgumentException("Expected comma");
            } while (true);
        }
    }
}
