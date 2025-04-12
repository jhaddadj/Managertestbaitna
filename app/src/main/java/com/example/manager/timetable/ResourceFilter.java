package com.example.manager.timetable;

import com.example.manager.admin.model.Resource;

/**
 * Interface for filtering resources during timetable generation.
 * Implementations can define criteria for which resources should be included or excluded.
 */
public interface ResourceFilter {
    
    /**
     * Determines whether a resource should be used in timetable generation.
     *
     * @param resource The resource to evaluate
     * @return true if the resource should be used, false if it should be excluded
     */
    boolean shouldUseResource(Resource resource);
}
