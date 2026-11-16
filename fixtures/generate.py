"""Regenerate structural test fixtures, not learner practice material. Standard library only."""
import json, struct, zlib
from pathlib import Path
root = Path(__file__).resolve().parent
for index, color in enumerate([(220,60,40),(20,140,60),(40,70,220)]):
    def chunk(kind, data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw = b''.join(b'\0'+bytes(color)*16 for _ in range(16))
    png = b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
    (root/'images'/f'asset-{index}.png').write_bytes(png)
def exercise(module, task, content):
    return dict(exerciseId=f'SYNTHETIC-{module}-{task}',taskNumber=task,source=dict(type='AI_GENERATED',url=None),content=content)
def save(module, tasks, exercises):
    name=f'{module.lower()}-{tasks}'
    (root/f'{name}.json').write_text(json.dumps(dict(schemaVersion=1,packageId=f'SYNTHETIC-{module}-{tasks}',module=module,exercises=exercises),indent=2)+'\n')
for task in range(1,5):
    if task==3:
        passage=[dict(type='PARAGRAPH',label=c,text=f'Synthetic paragraph {c}: the community room is open on weekday mornings.') for c in 'ABCD']
    elif task==2:
        passage=[dict(type='TABLE',headers=['Room','Hours'],rows=[['Library','09:00–17:00'],['Studio','10:00–16:00']])]
    else:
        passage=[dict(type='TEXT',text=f'Synthetic Part {task} passage. Dear Alex, the community meeting is on Tuesday at ten. Please bring the room booking form. Regards, Sam.')]
    shapes={1:[('MULTIPLE_CHOICE',6),('INLINE_BLANK',5)],2:[('INLINE_BLANK',5),('MULTIPLE_CHOICE',3)],3:[('PARAGRAPH_MATCH',9)],4:[('MULTIPLE_CHOICE',5),('INLINE_BLANK',5)]}[task]
    groups=[]; key={}; number=1
    for kind,count in shapes:
        group=dict(type=kind,questions=[]); tokens=[]
        for _ in range(count):
            qid=f'Q{number:02d}'; number+=1; tokens.append('{{'+qid+'}}')
            q=dict(id=qid,options=list('ABCDE') if task==3 else ['Tuesday','Wednesday','Thursday','Friday'])
            if kind!='INLINE_BLANK':q['prompt']=f'Synthetic question {qid}: which day is named?'
            group['questions'].append(q)
            key[qid]=dict(correctOption=4 if task==3 else 0,explanation='Synthetic validation key; this fixture is not assessment material.')
        if kind=='INLINE_BLANK':group['text']='Dear Sam, the selected values are '+', '.join(tokens)+'. Thank you, Alex.'
        groups.append(group)
    e=exercise('READING',task,dict(instructions='Synthetic test only. Choose an option for each item. For matching, E means information is not stated.',passage=passage,questionGroups=groups));e['answerKey']=key;save('READING',task,[e])
save('WRITING',1,[exercise('WRITING',1,dict(scenario='Synthetic scenario: your booked community room is unavailable.',instructions='Write an email to the manager in approximately 150–200 words.',requirements=['Describe the booking.','Explain the problem.','Request another room.']))])
save('WRITING',2,[exercise('WRITING',2,dict(title='Synthetic community survey',scenario='The centre is planning a new weekly activity.',instructions='Choose and support one option in approximately 150–200 words.',options=[dict(id='A',text='Morning walking group'),dict(id='B',text='Evening reading group')]))])
for task in [1,2,5,6,7,8]:
    c=dict(prompt=f'Synthetic Speaking Task {task}: tell your friend about a community activity.')
    if task==5:
        c['persuasionPrompt']='Persuade your friend to choose your selected activity instead of option C.'
        c['options']=[dict(id=letter,title=f'Synthetic choice {letter}',image=f'images/asset-{i}.png',details=[f'Cost: {10+i} dollars','Location: community centre']) for i,letter in enumerate('ABC')]
    if task==6:c['alternatives']=['Tell the organizer you will attend the morning activity.','Tell your friend you will attend the evening activity.']
    if task==8:c['image']='images/asset-0.png'
    save('SPEAKING',task,[exercise('SPEAKING',task,c)])
save('SPEAKING','3-4',[exercise('SPEAKING',t,dict(prompt=f'Synthetic Task {t}: '+('describe the scene.' if t==3 else 'predict what happens next.'),image='images/asset-0.png')) for t in [3,4]])
