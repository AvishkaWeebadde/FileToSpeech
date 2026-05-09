package fenix.aw.reader.service.impl;

import fenix.aw.reader.model.Job;
import fenix.aw.reader.model.JobStatus;
import fenix.aw.reader.repository.JobRepository;
import fenix.aw.reader.service.IJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class JobService implements IJobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final TTSClientService ttsClientService;

    public JobService(JobRepository jobRepository, TTSClientService ttsClientService) {
        this.jobRepository = jobRepository;
        this.ttsClientService = ttsClientService;
    }

    @Override
    public Job createJob(String pdfFileName) {
        Job job = new Job();
        job.setId(UUID.randomUUID().toString());
        job.setPdfFileName(pdfFileName);
        job.setStatus(JobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        logger.info("Created job {} for PDF: {}", saved.getId(), pdfFileName);
        return saved;
    }

    @Override
    public Job getJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    @Override
    @Async("ttsTaskExecutor")
    public void processJobAsync(String jobId, String pdfFilePath) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        try {
            job.setStatus(JobStatus.PROCESSING);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
            logger.info("Job {} started processing: {}", jobId, pdfFilePath);

            List<String> audioPaths = ttsClientService.processFileForTTS(pdfFilePath);

            if (audioPaths.isEmpty()) {
                throw new RuntimeException("No audio generated from PDF");
            }

            job.setTotalChunks(audioPaths.size());
            job.setProcessedChunks(audioPaths.size());
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);

            String outputPath = ttsClientService.combineAudioFiles(audioPaths, job.getPdfFileName());

            String outputFileName = Paths.get(outputPath).getFileName().toString();
            job.setOutputFileName(outputFileName);
            job.setStatus(JobStatus.COMPLETED);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
            logger.info("Job {} completed: {}", jobId, outputFileName);

        } catch (Exception ex) {
            logger.error("Job {} failed", jobId, ex);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }
}
