import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class DesktopCompiler extends JFrame {

    private JTextArea codeEditor;
    private JTextArea outputArea;
    private JComboBox<String> languageSelector;
    private JButton openButton;
    private JButton saveButton;
    private JButton runButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private JLabel fileLabel;

    private File currentFile = null;
    private Process runningProcess = null;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // Variables for the interactive console
    private BufferedWriter processWriter;
    private int consolePromptPosition = 0;
    private boolean isSystemAppending = false;

    private static final Color BG_DARK = new Color(30, 30, 30);
    private static final Color BG_EDITOR = new Color(40, 44, 52);
    private static final Color BG_PANEL = new Color(37, 37, 38);
    private static final Color BG_TOOLBAR = new Color(45, 45, 48);
    private static final Color FG_TEXT = new Color(220, 220, 220);
    private static final Color FG_DIM = new Color(140, 140, 140);
    private static final Color ACCENT_GREEN = new Color(78, 201, 176);
    private static final Color ACCENT_RED = new Color(240, 71, 71);
    private static final Color ACCENT_BLUE = new Color(86, 156, 214);
    private static final Color ACCENT_YELLOW = new Color(220, 220, 170);
    private static final Color BORDER_COLOR = new Color(62, 62, 66);

    private static final String[] LANGUAGES = {
            "Java", "Python", "C", "C++", "JavaScript"
    };

    public DesktopCompiler() {
        super("Desktop Compiler");
        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initUI() {
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0));
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setDefaultCode("Java");
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setBackground(BG_TOOLBAR);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftGroup.setOpaque(false);

        JLabel titleLabel = new JLabel("  Desktop Compiler");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(ACCENT_GREEN);
        leftGroup.add(titleLabel);
        leftGroup.add(makeSeparator());

        openButton = makeButton("Open", ACCENT_BLUE, "\uD83D\uDCC2");
        saveButton = makeButton("Save", ACCENT_YELLOW, "\uD83D\uDCBE");
        leftGroup.add(openButton);
        leftGroup.add(saveButton);
        leftGroup.add(makeSeparator());

        JLabel langLabel = new JLabel("Language:");
        langLabel.setForeground(FG_DIM);
        langLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        leftGroup.add(langLabel);

        languageSelector = new JComboBox<>(LANGUAGES);
        languageSelector.setBackground(BG_EDITOR);
        languageSelector.setForeground(FG_TEXT);
        languageSelector.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        languageSelector.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        languageSelector.setPreferredSize(new Dimension(130, 28));
        leftGroup.add(languageSelector);

        toolbar.add(leftGroup, BorderLayout.WEST);

        JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightGroup.setOpaque(false);

        runButton = makeButton("  Run", ACCENT_GREEN, "\u25B6");
        stopButton = makeButton("Stop", ACCENT_RED, "\u25A0");
        stopButton.setEnabled(false);

        rightGroup.add(runButton);
        rightGroup.add(stopButton);
        toolbar.add(rightGroup, BorderLayout.EAST);

        openButton.addActionListener(e -> openFile());
        saveButton.addActionListener(e -> saveFile());
        runButton.addActionListener(e -> runCode());
        stopButton.addActionListener(e -> stopProcess());
        languageSelector.addActionListener(e -> {
            String lang = (String) languageSelector.getSelectedItem();
            if (currentFile == null)
                setDefaultCode(lang);
        });

        return toolbar;
    }

    private JSeparator makeSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 22));
        sep.setForeground(BORDER_COLOR);
        return sep;
    }

    private JButton makeButton(String text, Color color, String icon) {
        JButton btn = new JButton(icon + " " + text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(BG_PANEL);
        btn.setForeground(color);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(color.darker().darker());
            }

            public void mouseExited(MouseEvent e) {
                btn.setBackground(BG_PANEL);
            }
        });
        return btn;
    }

    private JSplitPane buildMainPanel() {
        codeEditor = new JTextArea();
        codeEditor.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        codeEditor.setBackground(BG_EDITOR);
        codeEditor.setForeground(new Color(171, 178, 191));
        codeEditor.setCaretColor(ACCENT_GREEN);
        codeEditor.setSelectionColor(new Color(60, 100, 150));
        codeEditor.setLineWrap(false);
        codeEditor.setTabSize(4);
        codeEditor.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JScrollPane editorScroll = new JScrollPane(codeEditor);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        styleScrollPane(editorScroll);

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBackground(BG_PANEL);
        editorPanel.add(buildPanelHeader("CODE EDITOR", ACCENT_BLUE), BorderLayout.NORTH);
        editorPanel.add(editorScroll, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPanel, buildConsolePanel());
        mainSplit.setDividerLocation(700);
        mainSplit.setDividerSize(4);
        mainSplit.setBorder(null);
        mainSplit.setBackground(BORDER_COLOR);
        return mainSplit;
    }

    private JPanel buildConsolePanel() {
        outputArea = new JTextArea();
        outputArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
        outputArea.setBackground(new Color(25, 26, 28));
        outputArea.setForeground(new Color(200, 230, 200));
        outputArea.setCaretColor(ACCENT_GREEN);

        // Output area is now editable to accept standard input
        outputArea.setEditable(true);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        setupConsoleInteraction();

        JScrollPane outputScroll = new JScrollPane(outputArea);
        styleScrollPane(outputScroll);
        outputScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBackground(BG_PANEL);
        JPanel outputHeader = buildPanelHeader("OUTPUT / CONSOLE", ACCENT_GREEN);
        JButton clearOut = makeMiniButton("Clear", FG_DIM);
        clearOut.addActionListener(e -> {
            // 1. Temporarily turn OFF the security filter
            isSystemAppending = true;

            // 2. Clear the screen
            outputArea.setText("");
            consolePromptPosition = 0;

            // 3. Turn the security filter back ON
            isSystemAppending = false;
        });
        outputHeader.add(clearOut, BorderLayout.EAST);
        outputPanel.add(outputHeader, BorderLayout.NORTH);
        outputPanel.add(outputScroll, BorderLayout.CENTER);

        return outputPanel;
    }

    private void setupConsoleInteraction() {
        // DocumentFilter prevents users from deleting text printed by the system
        ((AbstractDocument) outputArea.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (isSystemAppending || (offset >= consolePromptPosition && runningProcess != null)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (isSystemAppending || (offset >= consolePromptPosition && runningProcess != null)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                if (isSystemAppending || (offset >= consolePromptPosition && runningProcess != null)) {
                    super.remove(fb, offset, length);
                }
            }
        });

        // KeyListener listens for the Enter key to send input to the running program
        outputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume(); // Prevent default new line behavior
                    if (runningProcess != null && runningProcess.isAlive() && processWriter != null) {
                        try {
                            int len = outputArea.getDocument().getLength();
                            String input = outputArea.getText(consolePromptPosition, len - consolePromptPosition);

                            // Manually jump to the next line in the UI
                            isSystemAppending = true;
                            outputArea.append("\n");
                            consolePromptPosition = outputArea.getDocument().getLength();
                            outputArea.setCaretPosition(consolePromptPosition);
                            isSystemAppending = false;

                            // Send input to the background process
                            processWriter.write(input + "\n");
                            processWriter.flush();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private JPanel buildPanelHeader(String title, Color accent) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(35, 36, 40));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(5, 12, 5, 10)));
        JLabel label = new JLabel(title);
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        label.setForeground(accent);
        header.add(label, BorderLayout.WEST);
        return header;
    }

    private JButton makeMiniButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setBackground(new Color(50, 50, 55));
        btn.setForeground(color);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(new Color(0, 122, 204));
        bar.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));

        fileLabel = new JLabel("No file opened");
        fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fileLabel.setForeground(Color.WHITE);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(Color.WHITE);

        bar.add(fileLabel, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.EAST);
        return bar;
    }

    private void styleScrollPane(JScrollPane pane) {
        pane.getVerticalScrollBar().setBackground(BG_PANEL);
        pane.getHorizontalScrollBar().setBackground(BG_PANEL);
        pane.getViewport().setBackground(BG_EDITOR);
    }

    private void setDefaultCode(String language) {
        switch (language) {
            case "Java":
                codeEditor.setText(
                        "import java.util.Scanner;\n\n" +
                                "public class Main {\n" +
                                "    public static void main(String[] args) {\n" +
                                "        Scanner sc = new Scanner(System.in);\n" +
                                "        System.out.print(\"Enter your name: \");\n" +
                                "        String name = sc.nextLine();\n" +
                                "        System.out.println(\"Hello, \" + name + \"!\");\n" +
                                "    }\n" +
                                "}\n");
                break;
            case "Python":
                codeEditor.setText(
                        "# Python Example\n" +
                                "name = input('Enter your name: ')\n" +
                                "print(f'Hello, {name}!')\n");
                break;
            case "C":
                codeEditor.setText(
                        "#include <stdio.h>\n\n" +
                                "int main() {\n" +
                                "    char name[100];\n" +
                                "    printf(\"Enter your name: \");\n" +
                                "    fflush(stdout);\n" +  // <-- THE FIX IS HERE
                                "    scanf(\"%s\", name);\n" +
                                "    printf(\"Hello, %s!\\n\", name);\n" +
                                "    return 0;\n" +
                                "}\n");
                break;
            case "C++":
                codeEditor.setText(
                        "#include <iostream>\n" +
                                "#include <string>\n" +
                                "using namespace std;\n\n" +
                                "int main() {\n" +
                                "    string name;\n" +
                                "    cout << \"Enter your name: \" << flush;\n" + // <-- THE FIX IS HERE
                                "    cin >> name;\n" +
                                "    cout << \"Hello, \" << name << \"!\" << endl;\n" +
                                "    return 0;\n" +
                                "}\n");
                break;
            case "JavaScript":
                codeEditor.setText(
                        "// JavaScript (Node.js)\n" +
                                "const readline = require('readline');\n" +
                                "const rl = readline.createInterface({\n" +
                                "    input: process.stdin,\n" +
                                "    output: process.stdout\n" +
                                "});\n" +
                                "rl.question('Enter your name: ', (name) => {\n" +
                                "    console.log('Hello, ' + name + '!');\n" +
                                "    rl.close();\n" +
                                "    process.exit(0);\n" + // <-- THE KILL SWITCH
                                "});\n");
                break;
        }
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Source File");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Source Files (*.java, *.py, *.c, *.cpp, *.js)",
                "java", "py", "c", "cpp", "js"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            try {
                String content = Files.readString(currentFile.toPath());
                codeEditor.setText(content);
                codeEditor.setCaretPosition(0);
                fileLabel.setText(currentFile.getAbsolutePath());
                autoSelectLanguage(getExtension(currentFile.getName()));
                setStatus("Opened: " + currentFile.getName());
            } catch (IOException ex) {
                showError("Failed to open file: " + ex.getMessage());
            }
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Source File");
            chooser.setSelectedFile(new File(getDefaultFileName((String) languageSelector.getSelectedItem())));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
                return;
            currentFile = chooser.getSelectedFile();
        }
        try {
            Files.writeString(currentFile.toPath(), codeEditor.getText());
            fileLabel.setText(currentFile.getAbsolutePath());
            setStatus("Saved: " + currentFile.getName());
        } catch (IOException ex) {
            showError("Failed to save file: " + ex.getMessage());
        }
    }

    private String getDefaultFileName(String lang) {
        switch (lang) {
            case "Java":
                return "Main.java";
            case "Python":
                return "main.py";
            case "C":
                return "main.c";
            case "C++":
                return "main.cpp";
            case "JavaScript":
                return "main.js";
            default:
                return "main.txt";
        }
    }

    private void autoSelectLanguage(String ext) {
        switch (ext.toLowerCase()) {
            case "java":
                languageSelector.setSelectedItem("Java");
                break;
            case "py":
                languageSelector.setSelectedItem("Python");
                break;
            case "c":
                languageSelector.setSelectedItem("C");
                break;
            case "cpp":
                languageSelector.setSelectedItem("C++");
                break;
            case "js":
                languageSelector.setSelectedItem("JavaScript");
                break;
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private void runCode() {
        String lang = (String) languageSelector.getSelectedItem();
        String code = codeEditor.getText().trim();
        if (code.isEmpty()) {
            showError("Code editor is empty.");
            return;
        }

        outputArea.setText("");
        consolePromptPosition = 0;
        setStatus("Running...");
        runButton.setEnabled(false);
        stopButton.setEnabled(true);

        executor.submit(() -> {
            File tempDir = null;
            try {
                tempDir = Files.createTempDirectory("desktop_compiler_").toFile();
                File sourceFile = writeSourceFile(lang, code, tempDir);
                String[] compileCmd = getCompileCommand(lang, sourceFile, tempDir);

                if (compileCmd != null) {
                    appendOutput("[Compiling...]\n");
                    int compileExit = executeProcess(compileCmd, tempDir, true);
                    if (compileExit != 0) {
                        appendOutput("\n[Compilation failed. Fix errors above and try again.]\n");
                        return;
                    }
                    appendOutput("[Compilation successful]\n\n");
                }

                appendOutput("[Running program...]\n\n");
                String[] runCmd = getRunCommand(lang, sourceFile, tempDir);
                int exitCode = executeProcess(runCmd, tempDir, false);

                // Only print exit code if it ran successfully (exitCode isn't our custom -1 failure flag)
                if (exitCode != -1) {
                    appendOutput("\n\n[Process exited with code: " + exitCode + "]\n");
                }

            } catch (IOException ex) {
                appendOutput("\n[IO Error: " + ex.getMessage() + "]\n");
            } catch (InterruptedException ex) {
                appendOutput("\n[Process interrupted]\n");
                Thread.currentThread().interrupt();
            } finally {
                File finalTempDir = tempDir;
                SwingUtilities.invokeLater(() -> {
                    runButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    setStatus("Finished");
                    runningProcess = null;
                });
                if (finalTempDir != null)
                    deleteDir(finalTempDir);
            }
        });
    }

    private File writeSourceFile(String lang, String code, File dir) throws IOException {
        String filename;
        switch (lang) {
            case "Java":
                filename = extractJavaClassName(code) + ".java";
                break;
            case "Python":
                filename = "main.py";
                break;
            case "C":
                filename = "main.c";
                break;
            case "C++":
                filename = "main.cpp";
                break;
            case "JavaScript":
                filename = "main.js";
                break;
            default:
                filename = "main.txt";
                break;
        }
        File file = new File(dir, filename);
        Files.writeString(file.toPath(), code);
        return file;
    }

    private String extractJavaClassName(String code) {
        for (String line : code.split("\n")) {
            line = line.trim();
            if (line.startsWith("public class ")) {
                String[] parts = line.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].equals("class")) {
                        String name = parts[i + 1];
                        int brace = name.indexOf('{');
                        if (brace >= 0)
                            name = name.substring(0, brace);
                        return name.trim();
                    }
                }
            }
        }
        return "Main";
    }

    private String[] getCompileCommand(String lang, File sourceFile, File dir) {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        String exeName = isWin ? "out.exe" : "out";

        switch (lang) {
            case "Java":
                return new String[] { "javac", sourceFile.getAbsolutePath() };
            case "C":
                return new String[] { "gcc", sourceFile.getAbsolutePath(), "-o",
                        new File(dir, exeName).getAbsolutePath() };
            case "C++":
                return new String[] { "g++", sourceFile.getAbsolutePath(), "-o",
                        new File(dir, exeName).getAbsolutePath() };
            default:
                return null;
        }
    }

    private String[] getRunCommand(String lang, File sourceFile, File dir) {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        String className = sourceFile.getName().replace(".java", "");
        String exeName = isWin ? "out.exe" : "out";
        String pythonCmd = isWin ? "python" : "python3";

        switch (lang) {
            case "Java":
                return new String[] { "java", "-cp", dir.getAbsolutePath(), className };
            case "Python":
                return new String[] { pythonCmd, sourceFile.getAbsolutePath() };
            case "C":
            case "C++":
                return new String[] { new File(dir, exeName).getAbsolutePath() };
            case "JavaScript":
                return new String[]{"node", sourceFile.getAbsolutePath()};
            default:
                return new String[] { "echo", "Unsupported language" };
        }
    }

    private int executeProcess(String[] command, File workDir, boolean isCompile)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            appendOutput("\n[SYSTEM ERROR: Cannot run '" + command[0] + "'. Ensure it is installed and added to your system PATH variables.]\n");
            return -1;
        }

        if (!isCompile) {
            synchronized (this) {
                runningProcess = process;
                processWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            }
        } else {
            // Close input stream for compiler since it won't ask for input
            process.getOutputStream().close();
        }

        // Use a character buffer to read standard output so prompts (with no \n) show up instantly
        try (InputStreamReader reader = new InputStreamReader(process.getInputStream())) {
            char[] buffer = new char[1024];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                final String text = new String(buffer, 0, bytesRead);
                appendOutput(text);
            }
        }

        int exitCode = process.waitFor();

        if (!isCompile) {
            synchronized (this) {
                if (processWriter != null) {
                    try {
                        processWriter.close();
                    } catch (Exception ignored) {
                    }
                    processWriter = null;
                }
            }
        }

        return exitCode;
    }

    private void stopProcess() {
        Process p;
        synchronized (this) {
            p = runningProcess;
        }
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            appendOutput("\n[Process stopped by user]\n");
        }
        SwingUtilities.invokeLater(() -> {
            runButton.setEnabled(true);
            stopButton.setEnabled(false);
            setStatus("Stopped");
        });
    }

    private void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            isSystemAppending = true;
            outputArea.append(text);
            // Move the input prompt position down past the newly appended text
            consolePromptPosition = outputArea.getDocument().getLength();
            outputArea.setCaretPosition(consolePromptPosition);
            isSystemAppending = false;
        });
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory())
                    deleteDir(f);
                else
                    f.delete();
            }
        }
        dir.delete();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        UIManager.put("ComboBox.background", new Color(40, 44, 52));
        UIManager.put("ComboBox.foreground", new Color(220, 220, 220));
        UIManager.put("ComboBox.selectionBackground", new Color(78, 201, 176));
        UIManager.put("ComboBox.selectionForeground", Color.BLACK);
        UIManager.put("Panel.background", new Color(30, 30, 30));
        UIManager.put("ScrollBar.background", new Color(37, 37, 38));
        UIManager.put("OptionPane.background", new Color(37, 37, 38));
        UIManager.put("OptionPane.messageForeground", new Color(220, 220, 220));

        SwingUtilities.invokeLater(DesktopCompiler::new);
    }
}
