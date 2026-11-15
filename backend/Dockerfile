FROM python:3.12-slim
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
ENV PYTHONUNBUFFERED=1
EXPOSE 8000
CMD ["uvicorn", "--factory", "app.main:create_app_from_env", "--host", "0.0.0.0", "--port", "8000"]
