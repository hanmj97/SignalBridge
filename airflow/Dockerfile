# Learning/local use only. Airflow standalone includes a SQLite metadata DB.
FROM apache/airflow:2.11.0-python3.11

# Install only the existing collector dependencies. Pin Airflow to prevent
# an accidental framework upgrade during pip dependency resolution.
COPY collector-requirements.txt /tmp/signalbridge-collector-requirements.txt
RUN pip install --no-cache-dir "apache-airflow==2.11.0" -r /tmp/signalbridge-collector-requirements.txt
