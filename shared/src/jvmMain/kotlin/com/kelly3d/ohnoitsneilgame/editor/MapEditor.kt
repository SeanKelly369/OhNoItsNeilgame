package com.kelly3d.ohnoitsneilgame.editor

import com.kelly3d.ohnoitsneilgame.maps.*
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import javax.swing.*

private fun demoAtlas(): BufferedImage {
    val image = BufferedImage(64, 16, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    val colors = listOf(Color(69, 147, 51), Color(139, 75, 30), Color(108, 118, 128), Color(155, 99, 46))
    colors.forEachIndexed { i, color ->
        g.color = color; g.fillRect(i * 16, 0, 16, 16)
        g.color = color.darker()
        for (y in 2..14 step 4) g.drawLine(i * 16, y, i * 16 + 14, y)
        g.color = color.brighter(); g.fillRect(i * 16, 0, 16, 3)
    }
    g.dispose()
    return image
}

private fun demoMap() = TileMap(30, 16, 16, 16, 4, 1, "demo_tiles.png").apply {
    setSpawn(3, 12)
    for (x in 0 until width) for (y in 14 until height) {
        paint(MapLayer.TERRAIN, x, y, if (y == 14) 0 else 1)
        markCollision(x, y, TileCollision.SOLID)
    }
    for (x in 8..13) {
        paint(MapLayer.TERRAIN, x, 10, 3)
        markCollision(x, 10, TileCollision.ONE_WAY)
    }
}

private class MapEditor : JPanel(BorderLayout()) {
    var map = demoMap()
        private set
    private var atlas = demoAtlas()
    private var selected = 0
    private var zoom = 2
    private var layer = MapLayer.TERRAIN
    private var tool = "Paint"
    private var collisionKind = TileCollision.SOLID
    private var showCollision = true
    private var showGrid = true
    private var file: File? = null
    private var saved = TileMapCodec.encode(map)
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var stroke: String? = null
    private var lastCell: Pair<Int, Int>? = null
    private val status = JLabel("Ready — select a tile, then drag to paint. Right-drag erases.")
    private val canvas = object : JPanel() {
        override fun getPreferredSize() = Dimension(map.width * map.tileWidth * zoom, map.height * map.tileHeight * zoom)
        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.color = Color(0, 122, 244); g.fillRect(0, 0, width, height)
            val w = map.tileWidth * zoom; val h = map.tileHeight * zoom
            for (y in 0 until map.height) for (x in 0 until map.width) {
                for (l in MapLayer.entries) {
                    val id = map.tile(l, x, y)
                    if (id >= 0) g.drawImage(atlas, x*w, y*h, (x+1)*w, (y+1)*h,
                        map.sourceX(id), map.sourceY(id), map.sourceX(id)+map.tileWidth, map.sourceY(id)+map.tileHeight, null)
                }
                val kind = map.collision(x, y)
                if (showCollision && kind != TileCollision.EMPTY) {
                    g.color = when (kind) {
                        TileCollision.SOLID -> Color(255, 50, 50, 100)
                        TileCollision.ONE_WAY -> Color(255, 220, 0, 140)
                        TileCollision.LADDER -> Color(0, 255, 255, 130)
                        else -> Color(255, 0, 255, 140)
                    }
                    g.fillRect(x*w, y*h, w, if (kind == TileCollision.ONE_WAY) 4 else h)
                }
                if (showGrid) { g.color = Color(0, 0, 0, 50); g.drawRect(x*w, y*h, w, h) }
            }
            g.color = Color.WHITE; g.stroke = BasicStroke(2f)
            g.drawRect(map.spawnX*w+2, map.spawnY*h+2, w-4, h-4)
            g.drawString("S", map.spawnX*w+5, map.spawnY*h+h-5)
            g.dispose()
        }
    }
    private val palette = object : JPanel() {
        override fun getPreferredSize() = Dimension(8 * 34, ((map.atlasColumns * map.atlasRows + 7) / 8) * 34)
        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g = graphics as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            for (id in 0 until map.atlasColumns * map.atlasRows) {
                val x = id % 8 * 34; val y = id / 8 * 34
                g.drawImage(atlas, x, y, x+32, y+32, map.sourceX(id), map.sourceY(id),
                    map.sourceX(id)+map.tileWidth, map.sourceY(id)+map.tileHeight, null)
                if (id == selected) { g.color = Color.YELLOW; g.drawRect(x, y, 32, 32) }
            }
        }
    }

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        fun button(label: String, action: () -> Unit) {
            toolbar.add(JButton(label).apply { addActionListener { safely(action) } })
        }
        button("New") { newMap() }
        button("Open…") { open() }
        button("Forest map") {
            if (canDiscard()) {
                val source = File("art/sprites/maps/forest/forest.neilmap")
                if (source.exists()) openFile(source)
                else adopt(ForestReconstruction.create(), ForestAssets.atlas(), null)
            }
        }
        button("Save…") { save() }
        button("Tileset…") { loadTileset() }
        button("Undo") { restore(undo, redo) }
        button("Redo") { restore(redo, undo) }
        button("Validate") {
            val errors = map.validationErrors()
            JOptionPane.showMessageDialog(this, if (errors.isEmpty()) "Map is valid." else errors.joinToString("\n"))
        }
        add(toolbar, BorderLayout.NORTH)
        val sidebar = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        fun label(value: String) { sidebar.add(JLabel(value)) }
        label("Layer")
        sidebar.add(JComboBox(MapLayer.entries.toTypedArray()).apply {
            selectedItem = layer; addActionListener { layer = selectedItem as MapLayer }
        })
        label("Tool")
        sidebar.add(JComboBox(arrayOf("Paint", "Erase", "Collision", "Spawn")).apply {
            addActionListener { tool = selectedItem as String }
        })
        label("Collision type")
        sidebar.add(JComboBox(TileCollision.entries.toTypedArray()).apply {
            selectedItem = collisionKind; addActionListener { collisionKind = selectedItem as TileCollision }
        })
        sidebar.add(JCheckBox("Collision overlay", true).apply {
            addActionListener { showCollision = isSelected; canvas.repaint() }
        })
        sidebar.add(JCheckBox("Grid", true).apply {
            addActionListener { showGrid = isSelected; canvas.repaint() }
        })
        label("Zoom")
        sidebar.add(JComboBox(arrayOf(1, 2, 3, 4)).apply {
            selectedItem = zoom; addActionListener { zoom = selectedItem as Int; refresh() }
        })
        label("Tiles — row-major IDs")
        sidebar.add(JScrollPane(palette).apply { preferredSize = Dimension(290, 250) })
        sidebar.add(JLabel("Red solid · Yellow one-way"))
        sidebar.add(JLabel("Cyan ladder · Magenta hazard"))
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, JScrollPane(canvas))
        split.dividerLocation = 300
        add(split, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        palette.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val id = e.y / 34 * 8 + e.x / 34
                if (e.x in 0 until 272 && id in 0 until map.atlasColumns * map.atlasRows) {
                    selected = id; palette.repaint(); status.text = "Selected tile $id"
                }
            }
        })
        val painter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                stroke = TileMapCodec.encode(map); lastCell = null; paintAt(e)
            }
            override fun mouseDragged(e: MouseEvent) { if (stroke != null) paintAt(e) }
            override fun mouseReleased(e: MouseEvent) {
                stroke?.let { if (it != TileMapCodec.encode(map)) { undo.addLast(it); trimHistory(); redo.clear() } }
                stroke = null; lastCell = null
                refresh()
            }
        }
        canvas.addMouseListener(painter); canvas.addMouseMotionListener(painter)
    }

    private fun paintAt(e: MouseEvent) {
        if (e.x < 0 || e.y < 0) { lastCell = null; return }
        val x = e.x / (map.tileWidth * zoom); val y = e.y / (map.tileHeight * zoom)
        if (!map.contains(x, y)) { lastCell = null; return }
        val erase = SwingUtilities.isRightMouseButton(e) || tool == "Erase"
        fun cell(cx: Int, cy: Int) {
            when (tool) {
                "Spawn" -> if (!erase) map.setSpawn(cx, cy)
                "Collision" -> map.markCollision(cx, cy, if (erase) TileCollision.EMPTY else collisionKind)
                else -> map.paint(layer, cx, cy, if (erase) -1 else selected)
            }
        }
        val previous = lastCell
        if (previous == null || tool == "Spawn") cell(x, y) else {
            // Bresenham avoids gaps when the pointer moves faster than events arrive.
            var px = previous.first; var py = previous.second
            val dx = kotlin.math.abs(x-px); val dy = -kotlin.math.abs(y-py)
            val sx = if (px < x) 1 else -1; val sy = if (py < y) 1 else -1
            var error = dx + dy
            while (true) {
                cell(px, py)
                if (px == x && py == y) break
                val twice = error * 2
                if (twice >= dy) { error += dy; px += sx }
                if (twice <= dx) { error += dx; py += sy }
            }
        }
        lastCell = x to y
        status.text = "Cell $x, $y · $layer · $tool · unsaved"
        canvas.repaint()
    }

    private fun safely(action: () -> Unit) {
        try { action() } catch (e: Exception) { JOptionPane.showMessageDialog(this, e.message, "Map kit", JOptionPane.ERROR_MESSAGE) }
    }
    fun canDiscard(): Boolean = saved == TileMapCodec.encode(map) ||
        JOptionPane.showConfirmDialog(this, "Discard unsaved map changes?", "Map kit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
    private fun trimHistory() { while (undo.size > 30) undo.removeFirst() }
    private fun restore(from: ArrayDeque<String>, to: ArrayDeque<String>) {
        if (from.isEmpty()) return
        to.addLast(TileMapCodec.encode(map)); map = TileMapCodec.decode(from.removeLast()); refresh()
    }
    private fun refresh() {
        canvas.revalidate(); canvas.repaint(); palette.revalidate(); palette.repaint()
        status.text = "${map.width} × ${map.height} · ${map.tileWidth} × ${map.tileHeight}px · ${file?.name ?: "Untitled"}" +
            if (saved != TileMapCodec.encode(map)) " · unsaved" else ""
    }
    private fun dimensions(prompt: String, defaults: String): Pair<Int, Int>? {
        val answer = JOptionPane.showInputDialog(this, prompt, defaults) ?: return null
        val values = answer.trim().split(Regex("[ x,]+")).map { it.toInt() }
        require(values.size == 2 && values.all { it in 1..256 }) { "Enter two integers from 1 to 256" }
        return values[0] to values[1]
    }
    private fun adopt(next: TileMap, image: BufferedImage, location: File?) {
        map = next; atlas = image; file = location; selected = 0
        undo.clear(); redo.clear(); saved = if (location == null) "" else TileMapCodec.encode(map)
        refresh()
    }
    private fun newMap() {
        if (!canDiscard()) return
        val size = dimensions("Map width and height in cells", "30 16") ?: return
        adopt(TileMap(size.first, size.second, map.tileWidth, map.tileHeight,
            map.atlasColumns, map.atlasRows, map.tileset), atlas, null)
    }
    private fun loadTileset() {
        if (!canDiscard()) return
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val source = chooser.selectedFile
        val image = ImageIO.read(source) ?: error("Not a supported image")
        val size = dimensions("Tile width and height in pixels (no margins or spacing)", "16 16") ?: return
        require(image.width % size.first == 0 && image.height % size.second == 0) { "Tile size must divide the image exactly" }
        val name = source.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".png"
        val next = TileMap(map.width, map.height, size.first, size.second, image.width / size.first, image.height / size.second, name)
        adopt(next, image, null)
        status.text = "Tileset loaded into a new empty map. Existing tile IDs were cleared."
    }
    private fun open() {
        if (!canDiscard()) return
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        openFile(chooser.selectedFile)
    }
    fun openFile(source: File) {
        require(source.length() <= 4_000_000) { "Map file is too large" }
        val next = TileMapCodec.decode(source.readText())
        val imageFile = File(source.parentFile, next.tileset)
        val image = if (imageFile.exists()) ImageIO.read(imageFile) else
            if (next.tileset == "demo_tiles.png") demoAtlas() else error("Missing tileset: $imageFile")
        require(image != null && image.width == next.atlasColumns * next.tileWidth &&
            image.height == next.atlasRows * next.tileHeight) { "Tileset dimensions do not match map" }
        adopt(next, image, source)
    }
    private fun save() {
        require(map.validationErrors().isEmpty()) { map.validationErrors().joinToString("\n") }
        val chooser = JFileChooser().apply { selectedFile = file ?: File("untitled.neilmap") }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val chosen = chooser.selectedFile.absoluteFile
        val destination = if (chosen.extension == "neilmap") chosen else File(chosen.path + ".neilmap")
        val imageFile = File(destination.parentFile, map.tileset)
        require(destination.canonicalFile != imageFile.canonicalFile) { "Map and tileset paths must differ" }
        if ((destination.exists() || imageFile.exists()) &&
            JOptionPane.showConfirmDialog(this, "Save map and tileset? Existing files will be replaced:\n$destination\n$imageFile",
                "Save assets", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return
        imageFile.parentFile.mkdirs()
        val imageTemp = Files.createTempFile(imageFile.parentFile.toPath(), "tiles-", ".png")
        val mapTemp = Files.createTempFile(destination.parentFile.toPath(), "map-", ".tmp")
        try {
            check(ImageIO.write(atlas, "png", imageTemp.toFile()))
            mapTemp.toFile().writeText(TileMapCodec.encode(map))
            Files.move(imageTemp, imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(mapTemp, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(imageTemp); Files.deleteIfExists(mapTemp) }
        file = destination; saved = TileMapCodec.encode(map); refresh()
    }

    fun smokeTest(output: File) {
        val encoded = TileMapCodec.encode(map)
        check(TileMapCodec.encode(TileMapCodec.decode(encoded)) == encoded)
        check(map.overlapsSolid(0f, 224f, 16f, 240f))
        check(!map.overlapsSolid(0f, 0f, 16f, 16f))
        canvas.setSize(canvas.preferredSize)
        val preview = BufferedImage(canvas.width, canvas.height, BufferedImage.TYPE_INT_RGB)
        val g = preview.createGraphics(); canvas.paint(g); g.dispose()
        check(ImageIO.write(preview, "png", output))
        println("Editor render and shared codec smoke test passed: $output")
    }
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--export-forest") {
        require(args.size == 2) { "Usage: --export-forest <empty-output-directory>" }
        ForestAssets.export(File(args[1]))
        return
    }
    SwingUtilities.invokeAndWait {
        val editor = MapEditor()
        if (args.firstOrNull() == "--open") {
            require(args.size == 2) { "Usage: --open <map.neilmap>" }
            editor.openFile(File(args[1]))
        }
        if (args.firstOrNull() == "--smoke-test") {
            editor.smokeTest(File(args.getOrElse(1) { "/tmp/neil-map-kit.png" }))
        } else {
            JFrame("Neil Map Kit — tile editor").apply {
                defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
                addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) { if (editor.canDiscard()) dispose() }
                })
                contentPane = editor
                setSize(1300, 760); setLocationRelativeTo(null); isVisible = true
            }
        }
    }
}
