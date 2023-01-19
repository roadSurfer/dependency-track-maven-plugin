package io.github.pmckeown.dependencytrack.project;

import io.github.pmckeown.dependencytrack.DependencyTrackException;
import io.github.pmckeown.dependencytrack.Response;
import io.github.pmckeown.dependencytrack.bom.BomParser;
import io.github.pmckeown.util.Logger;
import kong.unirest.HttpStatus;
import kong.unirest.UnirestException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.Optional;

import static java.lang.String.format;

@Singleton
public class ProjectAction {

    private ProjectClient projectClient;

    private BomParser bomParser;
    private Logger logger;

    @Inject
    public ProjectAction(ProjectClient projectClient, BomParser bomParser, Logger logger) {
        this.projectClient = projectClient;
        this.bomParser = bomParser;
        this.logger = logger;
    }

    public Project getProject(String projectName, String projectVersion) throws DependencyTrackException {
        try {
            Response<Project> response = projectClient.getProject(projectName, projectVersion);

            switch (response.getStatus()) {
                case HttpStatus.OK: {
                    Optional<Project> project = response.getBody();
                    if (project.isPresent()) {
                        return project.get();
                    } else {
                        throw new DependencyTrackException(
                                format("Requested project not found: %s-%s", projectName, projectVersion));
                    }
                }
                case HttpStatus.NOT_FOUND:
                    throw new DependencyTrackException(format("Requested project not found: %s-%s", projectName, projectVersion));
                default:
                    logger.error("Failed to find project '" + projectName +"' '" + projectVersion
                            + "' due to error from server: " + response.getStatusText());
                    throw new DependencyTrackException(format("Failed to fetch project from server: %s-%s", projectName, projectVersion));
            }
        } catch (UnirestException ex) {
            throw new DependencyTrackException(ex.getMessage(), ex);
        }
    }

    public boolean updateProjectInfo(Project project, String bomLocation) throws DependencyTrackException {
        Optional<ProjectInfo> info = bomParser.getProjectInfo(new File(bomLocation));
        if (info.isPresent()) {
            try {
                Response<Void> response = projectClient.patchProject(project.getUuid(), info.get());
                return response.isSuccess();
            } catch (UnirestException ex) {
                logger.error("Failed to update project info", ex);
                throw new DependencyTrackException("Failed to update project info");
            }
        } else {
            logger.warn("Could not create ProjectInfo from bom at location: %s", bomLocation);
        }

        return false;
    }

    boolean deleteProject(Project project) throws DependencyTrackException {
        try {
            logger.debug("Deleting project %s-%s", project.getName(), project.getVersion());

            Response<?> response = projectClient.deleteProject(project);
            return response.isSuccess();
        } catch(UnirestException ex) {
            logger.error("Failed to delete project", ex);
            throw new DependencyTrackException("Failed to delete project");
        }
    }
}
