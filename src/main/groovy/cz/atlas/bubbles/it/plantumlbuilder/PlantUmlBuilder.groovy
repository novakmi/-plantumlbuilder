/*
Copyright (c) 2011 bubbles.way@gmail.com

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

package cz.atlas.bubbles.it.plantumlbuilder

import cz.atlas.bubbles.it.nodebuilder.SimpleNode
import cz.atlas.bubbles.it.nodebuilder.SimpleNodeBuilder

//Listener interface  for node or attributes
enum PluginListenerResult {
        NOT_ACCEPTED, // node not accepted by plugin
        PROCESSED_STOP, // node processed by plugin, do not process node with other plugins
        PROCESSED_CONTINUE, // node processed by plugin, process node with other plugins as well
        FAILED, // node processing failed
}

interface PlantUmlBuilderPluginListener {
        /**
         * Process given node in plugin before and after plantuml builder
         * @param node builder node to process (
         * @param postProcess if false, it is pre processing time, if true, it is post processing time
         * @param opaque object to be passed from application using builder to plugin (can be null)
         * @return result
         * $see PluginListenerResult, SimpleNode
         */
        PluginListenerResult process(final SimpleNode node, boolean postProcess, Object opaque)
}

// Builder class
class PlantUmlBuilder extends SimpleNodeBuilder {
        private IndentPrinter out
        private PrintWriter writer
        private StringWriter stringWriter
        //private SimpleNode root = null //root node of the model
        private String builderText = null

        //see http://groovy.codehaus.org/gapi/groovy/beans/ListenerList.html
        @groovy.beans.ListenerList
        private List<PlantUmlBuilderPluginListener> pluginListeners = []

        public PlantUmlBuilder() {
                stringWriter = new StringWriter()
                writer = new PrintWriter(stringWriter)
                out = new IndentPrinter(writer)
                out.decrementIndent() // to start from beg. of line
        }

        /*
        // not needed anymore because of   @groovy.beans.ListenerList, use  addPlantUmlBuilderListener
        def addListener(final PlantUmlBuilderPluginListener listener) {
            pluginListeners += listener
        }
        */

        @Override
        protected void processNode(SimpleNode node, Object opaque) {
                switch (node.name) {
                        case 'plant':
                                opaque.printIndent()
                                opaque.println(node.value)
                                break
                        case 'title':
                                opaque.printIndent()
                                opaque.println("title $node.value")
                                break
                        case 'actor':
                        case 'participant':
                                opaque.printIndent()
                                opaque.print(node.name)
                                if (node.attributes.as) {
                                        opaque.println(" $node.value as $node.attributes.as")
                                } else {
                                        opaque.println(" $node.value")
                                }
                                break
                        case 'note':
                                opaque.printIndent()
                                opaque.print(node.name)
                                if (node.attributes.as) {
                                        opaque.println(" $node.value as $node.attributes.as")
                                } else {
                                        opaque.println(" ${node.attributes.pos ? "${node.attributes.pos} : " : ''}$node.value")
                                }
                                break
                        case 'plantuml':
                                if (root == node) {
                                        break
                                }
                        default:
                                println "Unsupported node name ${node.name}"
                                failed = true
                                break
                }
        }

        @Override
        protected void processNodeAfterChildrend(SimpleNode node, Object opaque) {
                opaque.decrementIndent()  
        }

        @Override
        protected void processNodeBeforeChildrend(SimpleNode node, Object opaque) {
                opaque.incrementIndent()
        }

        @Override protected processTree(rootNode, opaque) {
                boolean nodeProcessedByListener = false
                boolean failed = false
                for (l in pluginListeners) {
                        PluginListenerResult res = l.process(rootNode, false, opaque)
                        if (res == PluginListenerResult.FAILED) {
                                failed = true
                                break
                        }
                        nodeProcessedByListener = (res == PluginListenerResult.PROCESSED_STOP || res == PluginListenerResult.PROCESSED_CONTINUE)
                        if (res == PluginListenerResult.PROCESSED_STOP) {
                                break
                        }
                }
                if (!nodeProcessedByListener && !failed) {
                      processNode(rootNode, opaque)
                }

                if (rootNode.children.size()) {
                        processNodeBeforeChildrend(rootNode, opaque)
                        rootNode.children.each {
                                processTree(it, opaque)

                        }
                        processNodeAfterChildrend(rootNode, opaque)
                }
                if (nodeProcessedByListener && !failed) {
                        for (l in pluginListeners) {
                                PluginListenerResult res = l.process(rootNode, true, opaque)
                                if (res == PluginListenerResult.FAILED) {
                                        failed = true
                                        break
                                }
                                if (res == PluginListenerResult.PROCESSED_STOP) {
                                        break
                                }
                        }
                }
        }

        /**
         * Get PlantUML text build by the builder
         * @param params map with optional name params.
         *         Currently supported 'plainPlantUml' - do not add '@startuml/@enduml' to the returned PlantUML text
         *         getText()
         *         getText(plainPlantUml: true)
         * @return build text
         */
        public String getText(params) {
                def umlval = ''
                if (!builderText) {  // reuse from previous run?
                        StringBuffer buffer = stringWriter.getBuffer()
                        buffer.delete(0, buffer.length()) // clear buffer
                        stringWriter.flush()
                        if (root) {
                                processTree(root, out)
                        }
                        builderText = buffer.toString()
                }
                if (root?.value) {
                        umlval = " $root.value"
                }
                def retVal = ''
                if (!params?.plainPlantUml) {
                        retVal += "@startuml${umlval}\n"
                }
                retVal += builderText
                if (!params?.plainPlantUml) {
                        retVal += "@enduml"
                }
                return retVal
        }

        /**
         * Reset root element of the builder.
         * Use this method to start building PlantUML text from the beginning.
         */
        @Override public void reset() {
                root = null
                builderText = null
        }
       
}
