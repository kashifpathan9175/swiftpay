Postgres StatefulSet notes:
- Uses postgres:18 image
- Mounts ConfigMap postgres-init-scripts into /docker-entrypoint-initdb.d
- ConfigMap contains init-multiple-dbs.sh which will create the required databases
- PVC is created via pvc.yaml (1Gi)
- For Windows users, ensure ConfigMap line endings are LF and script has executable mode set via defaultMode: 0775
