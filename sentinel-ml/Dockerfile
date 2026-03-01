# Use an official lightweight Python image
FROM python:3.11-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the requirements file first (this leverages Docker layer caching)
COPY requirements.txt .

# Install dependencies without saving the cache to keep the image small
RUN pip install --no-cache-dir -r requirements.txt

# Copy the application code and your trained model
COPY main.py .
COPY fraud_model.cbm .

# Expose the port that FastAPI runs on
EXPOSE 8000

# Command to boot the server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]