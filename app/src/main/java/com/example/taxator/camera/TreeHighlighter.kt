package com.example.taxator.camera

import android.content.Context
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3

import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.ArFragment



class TreeHighlighter(context: Context, private val arFragment: ArFragment) {
    private val nodeMap = mutableMapOf<Anchor, Node>()
    private val material = MaterialFactory.makeOpaqueWithColor(context, Color(0F, 255F, 0F, 150F))
    private val lineThickness = 0.02F

    fun highlightTree(center: Vector3, size: Vector3){
        clearHighlights()

        // Создаем якорь в центре дерева
        val pose = Pose.makeTranslation(center.x, center.y, center.z)
        val anchor = arFragment.arSceneView.session?.createAnchor(pose) ?: return

        val lineNodes = createBoundingBox(size)
        val anchorNode = AnchorNode(anchor).apply {
            setParent(arFragment.arSceneView.scene)
        }

        lineNodes.forEach { lineNode ->
            lineNode.setParent(anchorNode)
            nodeMap[anchor] = lineNode
        }

    }

    private fun createBoundingBox(size: Vector3): List<Node> {
        val halfWidth = size.x / 2
        val halfHeight = size.y / 2

        return listOf(
            // Горизонтальные линии
            createLine(Vector3(-halfWidth, -halfHeight, 0f), Vector3(halfWidth, -halfHeight, 0f)),
            createLine(Vector3(-halfWidth, halfHeight, 0f), Vector3(halfWidth, halfHeight, 0f)),
            // Вертикальные линии
            createLine(Vector3(-halfWidth, -halfHeight, 0f), Vector3(-halfWidth, halfHeight, 0f)),
            createLine(Vector3(halfWidth, -halfHeight, 0f), Vector3(halfWidth, halfHeight, 0f))
        )
    }

    private fun createLine(start: Vector3, end: Vector3): Node {
        val lineLength = start.distanceTo(end)
        val lineDirection = Vector3.subtract(end, start).normalized()
        val lineRotation = Quaternion.lookRotation(lineDirection, Vector3.up())

        return Node().apply {
            renderable = ShapeFactory.makeCube(
                Vector3(lineThickness, lineThickness, lineLength),
                Vector3.zero(), material.get()
            )
            localRotation = lineRotation
            localPosition = Vector3.add(start, end).scaled(0.5f)
        }

    }

    fun clearHighlights() {
        nodeMap.keys.forEach { anchor ->
            (nodeMap[anchor]?.parent as? AnchorNode)?.anchor?.detach()
        }
        nodeMap.clear()
    }

    fun Vector3.distanceTo(other: Vector3): Float {
        return Vector3.subtract(this, other).length()
    }
}