# PDF Uploader and Reader Service

A backend service for uploading, storing, and processing PDF files, with the added functionality to convert them into audiobooks using a text-to-speech (TTS) feature. This project is designed for ease of use and extensibility, allowing users to manage PDFs efficiently and listen to their content on the go.

## Features

- **Upload PDFs**: Easily upload PDF files via a REST API.
- **Store PDFs**: Securely store uploaded PDFs with metadata for easy retrieval.
- **Text-to-Speech Conversion** : Generate audio versions of PDFs using a built-in TTS engine. The TTS service can be cloned via https://github.com/AvishkaWeebadde/tts-service
- **Swagger UI**: Explore and test API endpoints with an interactive Swagger interface.
- **Dockerized Deployment**: Run the service in a containerized environment for quick setup and scalability.

Addeed small improvements to parallel processing and cut down the processing time from 30 mins to about 13 seconds. Needs improvement
