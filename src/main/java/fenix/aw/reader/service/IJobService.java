package fenix.aw.reader.service;

import fenix.aw.reader.model.Job;

public interface IJobService {
    Job createJob(String pdfFileName);
    Job getJob(String jobId);
    void processJobAsync(String jobId, String pdfFilePath);
}
