FROM huggingface/transformers-cpu

RUN pip install --upgrade pip
RUN useradd -ms /bin/bash admin
RUN pip install flask pytorch-transformers keras
USER admin

WORKDIR /app
COPY run_bert_server.py /app
EXPOSE 8665
CMD ["python3", "-u", "run_bert_server.py", "/app"]
