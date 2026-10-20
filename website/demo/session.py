import sys,time
print('\033[2J\033[H\033]0;Infrastructure / demo workspace\007',end='')
print('\033[1;34mJASPER LAB\033[0m  /  infrastructure workspace\n')
print('\033[90mDemo environment · sample output · no live connections\033[0m\n')
print('\033[36moperator\033[0m  \033[90m~/infrastructure\033[0m')
print('$ kubectl get deployments -n staging\n')
print('NAME                 READY     UP-TO-DATE     AVAILABLE')
print('api-gateway          \033[32m3/3\033[0m       3              3')
print('worker-pool          \033[32m4/4\033[0m       4              4')
print('metrics              \033[32m2/2\033[0m       2              2\n')
print('\033[36moperator\033[0m  \033[90m~/infrastructure\033[0m')
print('$ ./scripts/check-services\n')
for s in ['Gateway         responding','Workers         ready','Metrics         collecting']:
 print('  \033[32m✓\033[0m  '+s)
print('\n\033[90m────────────────────────────────────────────────────────────\033[0m')
print('\n\033[32mAll systems ready.\033[0m  Your next move starts here.\n')
print('\033[36moperator\033[0m  \033[90m~/infrastructure\033[0m\n$ ',end='',flush=True)
while True:
 try:
  command = input().strip()
 except EOFError:
  break
 if command == 'kubectl get pods':
  print('\nNAME                        READY   STATUS')
  print('api-gateway-7d9f             \033[32m1/1     Running\033[0m')
  print('worker-pool-6c8b             \033[32m1/1     Running\033[0m')
  print('metrics-5f2a                 \033[32m1/1     Running\033[0m')
 elif command:
  print('Demo workspace: try kubectl get pods')
 print('\n\033[36moperator\033[0m  \033[90m~/infrastructure\033[0m\n$ ', end='', flush=True)
