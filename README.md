# eks-static-ip[![CircleCI](https://circleci.com/gh/hden/eks-static-ip/tree/master.svg?style=svg)](https://circleci.com/gh/hden/eks-static-ip/tree/master)
Assign static external IPs to EKS nodes.

## Usage
1. You will need to configure several static eips, identified by a common tag e.g. `EKS-NODE-POOL=foo`.
2. Configure worker nodes (a.k.a. node pool) with a common tag e.g. `EKS-IP-POOL=bar`.
3. Build bt running `make`.
4. Deploy this library to lambda by uploading the `eks-static-ip-0.1.0-standalone.jar`.
  - Handler: `eks.StaticIPHandler::handleRequest`
  - Runtime: `java8`
5. Configure the lambda with the following environmental variables:
  - `INETANCE_TAG_KEY` e.g. `EKS-NODE-POOL`
  - `INETANCE_TAG_VALUE` e.g. `foo`
  - `EIP_TAG_KEY` e.g. `EKS-IP-POOL`
  - `EIP_TAG_VALUE` e.g. `bar`

Trigger the lambda chronically e.g. by CloudWatch Events.
