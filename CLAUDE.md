# Instruções para o Claude

## Gambiarras: avisar antes

Quando uma melhoria proposta depender de contornar o próprio Android (interceptar gestos
do sistema, mexer na tela de bloqueio, adivinhar o que o sistema vai fazer, depender de um
comportamento que muda de aparelho para aparelho), diga **antes de começar** que é uma
gambiarra e que provavelmente não vai dar certo, explicando o porquê em poucas palavras.
Depois pergunte se quero continuar mesmo assim ou focar em outro detalhe do app.

Exemplo do que evitar: o gesto de home sobre a tela de descanso bloqueada pertence ao
Android, e o app só fica sabendo dele depois que a tela de bloqueio já apareceu. Tentar
trazer o descanso de volta levou quatro rodadas de teste no celular e sempre passava por
um pisca da tela de bloqueio; no fim, tudo foi removido.
