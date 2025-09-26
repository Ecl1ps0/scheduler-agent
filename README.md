Create docker network

```
docker network create jade-net
```

Build and run docker container

```
docker build -t scheduler-agent .

docker run -d --rm --name main-agent --network jade-net scheduler-agent
```