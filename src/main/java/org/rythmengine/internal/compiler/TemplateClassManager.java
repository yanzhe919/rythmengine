/**
 * Copyright (C) 2013-2016 The Rythm Engine project
 * for LICENSE and other details see:
 * https://github.com/rythmengine/rythmengine
 */
package org.rythmengine.internal.compiler;

import org.rythmengine.RythmEngine;
import org.rythmengine.logger.ILogger;
import org.rythmengine.logger.Logger;
import org.rythmengine.resource.ClasspathTemplateResource;
import org.rythmengine.resource.ITemplateResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: luog
 * Date: 18/01/12
 * Time: 8:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class TemplateClassManager {
    protected final static ILogger logger = Logger.get(TemplateClassManager.class);

    //private static final ILogger logger = Logger.get(TemplateClassCache.class);

    public RythmEngine engine = null;

    /**
     * Reference to the eclipse compiler.
     */
    TemplateCompiler compiler = null;
    /**
     * Index template class with class name
     */
    public Map<String, TemplateClass> clsNameIdx = new ConcurrentHashMap<String, TemplateClass>();
    /**
     * Index template class with inline template content or template file name
     */
    public Map<Object, TemplateClass> tmplIdx = new HashMap<Object, TemplateClass>();

    public TemplateClassManager(RythmEngine engine) {
        if (null == engine) throw new NullPointerException();
        this.engine = engine;
        this.compiler = new TemplateCompiler(this);
    }

    /**
     * Clear the classCache cache
     */
    public void clear() {
        clsNameIdx = new ConcurrentHashMap<String, TemplateClass>();
        tmplIdx = new HashMap<Object, TemplateClass>();
    }

    /**
     * All loaded classes.
     *
     * @return All loaded classes
     */
    public List<TemplateClass> all() {
        return new CopyOnWriteArrayList<TemplateClass>(clsNameIdx.values());
    }

    /**
     * Get a class by name
     *
     * @param name The fully qualified class name
     * @return The TemplateClass or null
     */
    public TemplateClass getByClassName(String name) {
        TemplateClass tc = clsNameIdx.get(name);
        checkUpdate(tc);
        return tc;
    }
    
    public TemplateClass getByTemplate(Object name, boolean checkResource) {
        TemplateClass tc = tmplIdx.get(name);
        if (checkResource && null == tc) {
            // try to see if resourceLoader has some kind of name transform
            ITemplateResource r = engine.resourceManager().getResource(name.toString());
            if (!r.isValid()) {
                return null;
            }
            tc = tmplIdx.get(r.getKey());
        }
        checkUpdate(tc);
        return tc;
    }

    public TemplateClass getByTemplate(Object name) {
        return getByTemplate(name, true);
    }

    private void checkUpdate(TemplateClass tc) {
        if (null == tc || engine.isProdMode()) {
            return;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("checkUpdate for template: %s", tc.getKey());
        }
        try {
            engine.classLoader().detectChange(tc);
        } catch (ClassReloadException e) {
            engine.restart(e);
        }
    }

    List<TemplateClass> getEmbeddedClasses(String name) {
        List<TemplateClass> l = new ArrayList<TemplateClass>();
        for (Map.Entry<String, TemplateClass> entry : clsNameIdx.entrySet()) {
            if (entry.getKey().startsWith(name + "$")) {
                l.add(entry.getValue());
            }
        }
        return l;
    }
    
    @Deprecated
    public void add(Object key, TemplateClass templateClass) {
        //tmplIdx.put(key, templateClass);
    }

    public void add(TemplateClass templateClass) {
        //clsNameIdx.put(templateClass.name0(), templateClass);
        clsNameIdx.put(templateClass.name(), templateClass);
        if (!templateClass.isInner()) {
            ITemplateResource rsrc = templateClass.templateResource;
            Object key = rsrc.getKey();
            tmplIdx.put(key, templateClass);
            if (rsrc instanceof ClasspathTemplateResource) {
                String key2 = ((ClasspathTemplateResource) rsrc).getKey2();
                if (key != key2) {
                    tmplIdx.put(key2, templateClass);
                }
            }
        }
    }

    public void remove(TemplateClass templateClass) {
        if (null == templateClass) return;
        if (templateClass.isInner()) {
            clsNameIdx.remove(templateClass.name());
            return;
        }
        // remove versioned link
        clsNameIdx.remove(templateClass.name());
        // remove unversioned link
        String name0 = templateClass.name0();
        clsNameIdx.remove(name0);
        List<String> embedded = new ArrayList<String>();
        for (String cn : clsNameIdx.keySet()) {
            if (cn.matches(name0 + "v[0-9]+\\$.*")) embedded.add(cn);
        }
        for (String cn : embedded) clsNameIdx.remove(cn);
        if (null != templateClass && null != templateClass.templateResource) tmplIdx.remove(templateClass.getKey());
    }

    public void remove(String name) {
        TemplateClass templateClass = clsNameIdx.get(name);
        remove(templateClass);
    }

    public boolean hasClass(String name) {
        return clsNameIdx.containsKey(name);
    }

    @Override
    public String toString() {
        return clsNameIdx.toString();
    }
}
